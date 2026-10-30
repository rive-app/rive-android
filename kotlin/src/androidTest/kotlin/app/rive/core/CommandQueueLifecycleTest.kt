package app.rive.core

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.system.Os
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.Fit
import app.rive.RiveAndroidTest
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.runtime.kotlin.test.R
import app.rive.semantics.SemanticTreeModel
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandQueueLifecycleTest : RiveAndroidTest() {
    @Test
    fun release_fromCallerThread_disposes() {
        val commandQueue = CommandQueue()

        commandQueue.release(
            "CommandQueueLifecycleTest",
            "Final release from caller thread",
        )

        assertDisposed(commandQueue)
    }

    /**
     * Exercises Vulkan worker teardown with an image retained by the command server.
     *
     * The decoded handle is intentionally left in the command server. Drivers that strictly
     * enforce VkDevice lifetime requirements can crash during [RiveWorker.release] if the command
     * server outlives the render context.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun release_withCachedVulkanImage_doesNotCrash() {
        val imageBytes = context.resources.openRawResource(R.raw.eve).use { it.readBytes() }
        val worker = RiveWorker(renderContext = RenderContextVulkan())

        try {
            worker.withPolling {
                runBlocking {
                    worker.decodeImage(imageBytes)
                }
            }
        } finally {
            worker.release("CommandQueueLifecycleTest", "Vulkan image teardown test cleanup")
            assertDisposed(worker)
        }
    }

    /**
     * Verifies worker teardown releases embedded image resources retained by a native file.
     *
     * Use a raw file handle because a public RiveFile retains the worker and prevents final
     * release while open. Intentionally omit deleteFile so CommandServer destroys the file.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun release_withCachedVulkanImageFile_doesNotCrash() {
        val fileBytes = context.resources.openRawResource(R.raw.asset_load_check).use {
            it.readBytes()
        }
        val worker = RiveWorker(renderContext = RenderContextVulkan())

        try {
            worker.withPolling {
                runBlocking {
                    worker.loadFile(fileBytes)
                }
            }
        } finally {
            worker.release("CommandQueueLifecycleTest", "Vulkan file teardown test cleanup")
            assertDisposed(worker)
        }
    }

    @Test
    fun release_fromBackground_clearsSemanticTreesOnMain() {
        val commandQueue = CommandQueue()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val stateMachineHandle = StateMachineHandle(1L)

        instrumentation.runOnMainSync {
            commandQueue.semanticTree(stateMachineHandle)
        }

        val releaseThread = Thread {
            commandQueue.release(
                "CommandQueueLifecycleTest",
                "Final release from background thread",
            )
        }
        releaseThread.start()
        releaseThread.join(2_000)
        assertFalse(releaseThread.isAlive, "Background release did not return")

        var hasTreeAfterRelease = true
        instrumentation.runOnMainSync {
            hasTreeAfterRelease = commandQueue.hasSemanticTree(stateMachineHandle)
        }

        assertFalse(hasTreeAfterRelease, "Semantic-tree ownership was not cleared on main")
        assertDisposed(commandQueue)
    }

    @Test
    fun release_fromCommandServerThread_throws() {
        val commandQueue = CommandQueue()
        val releaseAttempted = CountDownLatch(1)
        val thrown = AtomicReference<Throwable?>()

        commandQueue.runOnCommandServer {
            try {
                commandQueue.release(
                    "CommandQueueLifecycleTest",
                    "Final release from command server thread",
                )
            } catch (t: Throwable) {
                thrown.set(t)
            } finally {
                releaseAttempted.countDown()
            }
        }

        assertTrue(
            releaseAttempted.await(2, TimeUnit.SECONDS),
            "Command server release callback did not run",
        )
        assertTrue(
            thrown.get() is IllegalStateException,
            "Expected command server release to throw IllegalStateException",
        )
        assertFalse(
            commandQueue.isDisposed,
            "CommandQueue should not be disposed after failed release attempt",
        )

        commandQueue.release(
            "CommandQueueLifecycleTest",
            "Final release from caller thread",
        )
        assertDisposed(commandQueue)
    }

    @Test
    fun beginPolling_whenDisposed_exits() = runBlocking {
        val commandQueue = CommandQueue()
        val lifecycleOwner = TestLifecycleOwner()

        // Latches let the test thread observe ticker progress. CompletableDeferred gates let the
        // suspending ticker pause without blocking the main dispatcher.
        val firstFrameRequested = CountDownLatch(1)
        val secondFrameRequested = CountDownLatch(1)
        val firstFrameMayContinue = CompletableDeferred<Unit>()
        val secondFrameMayContinue = CompletableDeferred<Unit>()
        val frameCount = AtomicInteger()

        val ticker = FrameTicker { onFrame ->
            when (frameCount.incrementAndGet()) {
                1 -> {
                    firstFrameRequested.countDown()
                    firstFrameMayContinue.await()
                }

                else -> {
                    secondFrameRequested.countDown()
                    secondFrameMayContinue.await()
                }
            }
            onFrame(0L)
        }

        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.CREATED
        }
        // Begin the polling loop on main (as it would be in production) with our test lifecycle
        // owner and ticker.
        val polling = async(Dispatchers.Main.immediate) {
            commandQueue.beginPolling(lifecycleOwner.lifecycle, ticker)
        }
        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.RESUMED
        }

        assertTrue(
            firstFrameRequested.await(2, TimeUnit.SECONDS),
            "Polling loop did not request a frame",
        )
        // Release while we are in the middle of a frame tick, before polling. This would trigger an
        // exception when polling the released command queue if polling is incorrectly handling
        // disposal.
        commandQueue.release(
            "CommandQueueLifecycleTest",
            "Disposed while polling",
        )
        firstFrameMayContinue.complete(Unit)

        // Confirm that the polling loop does not request another frame after disposal, i.e. expect
        // a timeout.
        assertFalse(
            secondFrameRequested.await(250, TimeUnit.MILLISECONDS),
            "Polling requested another frame after the command queue was disposed",
        )
        // Destroy the lifecycle only after the above assertion, proving that the polling loop
        // stopped due to disposal rather than lifecycle teardown.
        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.DESTROYED
        }
        secondFrameMayContinue.complete(Unit)

        // Confirm that the polling loop exits and the command queue is disposed.
        withTimeout(2_000) {
            polling.await()
        }
        assertDisposed(commandQueue)
    }

    @Test
    fun beginPolling_fromBackground_confinesPollingToMain() = runBlocking {
        val commandQueue = CommandQueue()
        val lifecycleOwner = TestLifecycleOwner()
        val tickerRanOnMain = CompletableDeferred<Boolean>()
        val holdFrame = CompletableDeferred<Unit>()
        val ticker = FrameTicker { onFrame ->
            tickerRanOnMain.complete(Looper.myLooper() == Looper.getMainLooper())
            holdFrame.await()
            onFrame(0L)
        }

        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.CREATED
        }
        val polling = async(Dispatchers.Default) {
            commandQueue.beginPolling(lifecycleOwner.lifecycle, ticker)
        }
        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.RESUMED
        }

        assertTrue(
            withTimeout(2_000) { tickerRanOnMain.await() },
            "Polling ticker did not run on the main thread",
        )

        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.DESTROYED
        }
        withTimeout(2_000) { polling.await() }
        commandQueue.release(
            "CommandQueueLifecycleTest",
            "Final release after main-thread polling",
        )
        assertDisposed(commandQueue)
    }

    @Test
    fun pollMessages_offMainThread_throws() = runBlocking {
        val commandQueue = CommandQueue()

        val thrown = withContext(Dispatchers.Default) {
            assertFailsWith<IllegalStateException> { commandQueue.pollMessages() }
        }

        assertTrue(thrown.message?.contains("main thread") == true)
        commandQueue.release(
            "CommandQueueLifecycleTest",
            "Final release after off-main poll",
        )
        assertDisposed(commandQueue)
    }

    /**
     * A callback delivered by [CommandQueue.pollMessages] can resume a coroutine before the poll
     * returns. If that coroutine releases the last owning reference, as an app does to recover from
     * a lost render context, disposal waits until the poll returns.
     */
    @Test
    fun pollMessages_lastReleaseFromCallback_disposesAfterPoll() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = context.resources.openRawResource(R.raw.multipleartboards).use {
            it.readBytes()
        }
        val commandQueue = CommandQueue()
        val polling = AtomicBoolean(false)
        val releasedDuringPoll = AtomicReference<Boolean?>(null)
        val disposedAtRelease = AtomicReference<Boolean?>(null)
        instrumentation.runOnMainSync {
            // The polls below run outside any coroutine, so Main.immediate resumes this coroutine
            // inside the poll that delivers the file callback.
            CoroutineScope(Dispatchers.Main.immediate).launch {
                RiveFile.load(RiveFileSource.Bytes(bytes), commandQueue).close()
                commandQueue.release(
                    "CommandQueueLifecycleTest",
                    "Last release from a polled callback",
                )
                disposedAtRelease.set(commandQueue.isDisposed)
                releasedDuringPoll.set(polling.get())
            }
        }

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (releasedDuringPoll.get() == null && System.nanoTime() < deadline) {
            instrumentation.runOnMainSync {
                polling.set(true)
                try {
                    commandQueue.pollMessages()
                } finally {
                    polling.set(false)
                }
            }
            Thread.sleep(16)
        }

        assertTrue(releasedDuringPoll.get() == true, "The last release should run during a poll")
        assertFalse(disposedAtRelease.get() == true, "Disposal should wait for the poll to return")
        assertDisposed(commandQueue)
    }

    @Test
    fun semanticsDiffCallback_appliesTreeOnMainThread() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.tabtest) {
                ViewModelInstance.fromFile(
                    file,
                    ViewModelSource.DefaultForArtboard(artboard).defaultInstance(),
                ).use { viewModelInstance ->
                    riveWorker.bindViewModelInstance(
                        stateMachine.stateMachineHandle,
                        viewModelInstance.instanceHandle,
                    )

                    lateinit var tree: SemanticTreeModel
                    instrumentation.runOnMainSync {
                        tree = stateMachine.semanticTree
                    }

                    val appliedOnMain = CompletableDeferred<Boolean>()
                    // An unconfined collector resumes on the StateFlow emitter's thread, exposing
                    // the thread on which the JNI callback completed diff application.
                    val observer = launch(
                        Dispatchers.Unconfined,
                        start = CoroutineStart.UNDISPATCHED,
                    ) {
                        tree.versionFlow.drop(1).first()
                        appliedOnMain.complete(Looper.myLooper() == Looper.getMainLooper())
                    }

                    stateMachine.enableSemantics()
                    repeat(10) {
                        stateMachine.advance(100.milliseconds)
                    }
                    stateMachine.drainSemanticsDiff(
                        fit = Fit.Contain(),
                        surfaceWidth = 500f,
                        surfaceHeight = 500f,
                    )

                    assertTrue(
                        withTimeout(2_000) { appliedOnMain.await() },
                        "Semantic diff was not applied on the main thread",
                    )
                    observer.join()

                    var nodeCount = 0
                    instrumentation.runOnMainSync {
                        nodeCount = tree.nodeCount
                    }
                    assertTrue(nodeCount > 0, "Expected the native semantic diff to add nodes")
                }
            }
        }
    }

    @Test
    fun deleteStateMachine_nativeCallbackRemovesSemanticTree() = runBlocking {
        val commandQueue = CommandQueue()
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            commandQueue.withPolling {
                withDefaultRiveResources(R.raw.empty) {
                    val stateMachineHandle = stateMachine.stateMachineHandle
                    lateinit var originalTree: SemanticTreeModel

                    instrumentation.runOnMainSync {
                        originalTree = commandQueue.semanticTree(stateMachineHandle)
                        stateMachine.close()
                    }

                    var currentTree = originalTree
                    val timeoutAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                    while (currentTree === originalTree && System.nanoTime() < timeoutAt) {
                        instrumentation.runOnMainSync {
                            currentTree = commandQueue.semanticTree(stateMachineHandle)
                        }
                        if (currentTree === originalTree) {
                            Thread.sleep(10)
                        }
                    }

                    assertNotSame(
                        originalTree,
                        currentTree,
                        "Native deletion callback did not remove the tree",
                    )
                }
            }
        } finally {
            commandQueue.release(
                "CommandQueueLifecycleTest",
                "Final release after state machine deletion",
            )
            assertDisposed(commandQueue)
        }
    }

    @Test
    fun beginPolling_withFramesWithheld_deliversRepliesOnWake() = runBlocking {
        val commandQueue = CommandQueue()
        val lifecycleOwner = TestLifecycleOwner()
        val listening = CompletableDeferred<Unit>()
        val polling = beginWakeOnlyPolling(commandQueue, lifecycleOwner) {
            listening.complete(Unit)
        }
        withTimeout(2_000) { listening.await() }

        withContext(Dispatchers.Main.immediate) {
            withTimeout(2_000) { loadFile(commandQueue).close() }
        }

        stopPolling(commandQueue, lifecycleOwner, polling)
    }

    @Test
    fun beginPolling_replyQueuedWhilePaused_deliversOnResume() = runBlocking {
        val commandQueue = CommandQueue()
        val lifecycleOwner = TestLifecycleOwner()
        val starts = AtomicInteger()
        val resumedTwice = CompletableDeferred<Unit>()
        val firstStart = CompletableDeferred<Unit>()
        val polling = beginWakeOnlyPolling(commandQueue, lifecycleOwner) {
            when (starts.incrementAndGet()) {
                1 -> firstStart.complete(Unit)
                else -> resumedTwice.complete(Unit)
            }
        }
        withTimeout(2_000) { firstStart.await() }
        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.STARTED
        }

        val file = async(Dispatchers.Main.immediate) { loadFile(commandQueue) }
        delay(300)
        assertFalse(file.isCompleted, "A reply arrived while polling was paused")
        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.RESUMED
        }
        withTimeout(2_000) { resumedTwice.await() }
        withTimeout(2_000) { file.await() }.close()

        stopPolling(commandQueue, lifecycleOwner, polling)
    }

    @Test
    fun beginPolling_repeatedCycles_closeTheirDescriptors() = runBlocking {
        // The first cycle opens driver files that stay open for the process.
        pollingCycle()
        val before = openDescriptorCount()
        repeat(10) { pollingCycle() }

        // Leaks would add at least one per cycle; the margin absorbs unrelated descriptors.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (openDescriptorCount() - before >= 5 && System.nanoTime() < deadline) {
            delay(50)
        }
        val growth = openDescriptorCount() - before
        assertTrue(growth < 5, "Open descriptors grew by $growth over 10 polling cycles")
    }

    /**
     * Releases the worker after the command server signaled a reply but before the main thread
     * consumed it, so the listener must skip polling the released queue.
     */
    @Test
    fun release_withWakePending_skipsPollAndClosesDescriptors() = runBlocking {
        // The first cycle opens driver files that stay open for the process.
        pollingCycle()
        val eventfdsBefore = openEventfdCount()
        val commandQueue = CommandQueue()
        val lifecycleOwner = TestLifecycleOwner()
        val listening = CompletableDeferred<Unit>()
        val polling = beginWakeOnlyPolling(commandQueue, lifecycleOwner) {
            listening.complete(Unit)
        }
        withTimeout(2_000) { listening.await() }
        val wakeFd = commandQueue.wakeFd()

        val bytes = context.resources.openRawResource(R.raw.multipleartboards).use {
            it.readBytes()
        }
        val file = CompletableDeferred<Result<FileHandle>>()
        val mainHeld = CountDownLatch(1)
        val releaseMain = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            // Submits the load, then holds the main thread before its reply can be polled. Unlike
            // RiveFile.load, the request holds no reference, so the release below is the last.
            CoroutineScope(Dispatchers.Main.immediate).launch {
                file.complete(runCatching { commandQueue.loadFile(bytes) })
            }
            mainHeld.countDown()
            releaseMain.await()
        }
        try {
            assertTrue(mainHeld.await(2, TimeUnit.SECONDS), "The main thread was not held")
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (eventfdCount(wakeFd) == 0L && System.nanoTime() < deadline) {
                delay(5)
            }
            assertTrue(eventfdCount(wakeFd) > 0L, "The command server did not signal the reply")
            commandQueue.release("CommandQueueLifecycleTest", "Released with a wake pending")
            assertTrue(commandQueue.awaitShutdown(2_000), "Native shutdown did not finish")
        } finally {
            releaseMain.countDown()
        }

        assertTrue(withTimeout(2_000) { file.await() }.isFailure, "The load was not cancelled")
        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.DESTROYED
        }
        withTimeout(2_000) { polling.await() }
        assertDisposed(commandQueue)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (openEventfdCount() > eventfdsBefore && System.nanoTime() < deadline) {
            delay(50)
        }
        assertTrue(openEventfdCount() <= eventfdsBefore, "The wake descriptors stayed open")
    }

    @Test
    fun beginPolling_releasedWhilePaused_staysStoppedOnResume() = runBlocking {
        val commandQueue = CommandQueue()
        val lifecycleOwner = TestLifecycleOwner()
        val starts = AtomicInteger()
        val firstStart = CompletableDeferred<Unit>()
        val polling = beginWakeOnlyPolling(commandQueue, lifecycleOwner) {
            starts.incrementAndGet()
            firstStart.complete(Unit)
        }
        withTimeout(2_000) { firstStart.await() }
        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.STARTED
        }

        commandQueue.release("CommandQueueLifecycleTest", "Released while paused")
        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.RESUMED
        }
        // Lets the resumed polling block run to its end on the main thread.
        withContext(Dispatchers.Main) {}
        assertEquals(1, starts.get(), "Polling restarted on a released queue")

        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.DESTROYED
        }
        withTimeout(2_000) { polling.await() }
        assertDisposed(commandQueue)
    }

    private suspend fun pollingCycle() = coroutineScope {
        val commandQueue = CommandQueue()
        val lifecycleOwner = TestLifecycleOwner()
        val listening = CompletableDeferred<Unit>()
        val polling = beginWakeOnlyPolling(commandQueue, lifecycleOwner) {
            listening.complete(Unit)
        }
        withTimeout(2_000) { listening.await() }
        stopPolling(commandQueue, lifecycleOwner, polling)
    }

    /**
     * Polls with a ticker that never delivers a frame, so only the reply wake can poll.
     *
     * @param onListening Runs each time polling starts, after its reply listener is registered.
     */
    private suspend fun CoroutineScope.beginWakeOnlyPolling(
        commandQueue: CommandQueue,
        lifecycleOwner: TestLifecycleOwner,
        onListening: () -> Unit,
    ): Deferred<Unit> {
        // Older versions cannot watch the wake, so only frames poll there.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        val ticker = FrameTicker {
            onListening()
            awaitCancellation()
        }
        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.RESUMED
        }
        return async(Dispatchers.Main.immediate) {
            commandQueue.beginPolling(lifecycleOwner.lifecycle, ticker)
        }
    }

    private suspend fun stopPolling(
        commandQueue: CommandQueue,
        lifecycleOwner: TestLifecycleOwner,
        polling: Deferred<Unit>,
    ) {
        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.DESTROYED
        }
        withTimeout(2_000) { polling.await() }
        commandQueue.release("CommandQueueLifecycleTest", "Final release after wake polling")
        assertDisposed(commandQueue)
    }

    private suspend fun loadFile(commandQueue: CommandQueue): RiveFile {
        val bytes = context.resources.openRawResource(R.raw.multipleartboards).use {
            it.readBytes()
        }
        return RiveFile.load(RiveFileSource.Bytes(bytes), commandQueue)
    }

    private fun openDescriptorCount() = File("/proc/self/fd").list()?.size ?: 0

    private fun openEventfdCount() = File("/proc/self/fd").list()?.count {
        runCatching { Os.readlink("/proc/self/fd/$it") }.getOrNull() == "anon_inode:[eventfd]"
    } ?: 0

    /** @return The signal count pending on the eventfd [fd], read without consuming it. */
    private fun eventfdCount(fd: Int): Long = File("/proc/self/fdinfo/$fd").readLines()
        .first { it.startsWith("eventfd-count:") }
        .substringAfter(':').trim().toLong(16)

    /**
     * Minimal lifecycle owner for tests that need to drive [Lifecycle.State] transitions directly.
     *
     * [CommandQueue.beginPolling] uses `repeatOnLifecycle`, so these tests need deterministic
     * control over when the polling block starts, stops, and completes without depending on an
     * Activity or Fragment lifecycle.
     */
    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry

        var currentState: Lifecycle.State
            get() = registry.currentState
            set(value) {
                registry.currentState = value
            }
    }
}
