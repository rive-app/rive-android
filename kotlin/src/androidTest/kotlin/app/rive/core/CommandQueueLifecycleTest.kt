package app.rive.core

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
            worker.run {
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
            worker.run {
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

    /** Automatic delivery holds the worker alive until inline callback continuations return. */
    @Test
    fun automaticDelivery_lastReleaseFromCallback_disposesAfterDrain() = runBlocking {
        val worker = CommandQueue()
        val released = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main.immediate) {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                loadFile(worker).close()
                worker.release("CommandQueueLifecycleTest", "Release inside callback")
                released.complete(worker.isDisposed)
            }
        }
        assertFalse(withTimeout(5_000) { released.await() }, "Disposed inside callback")
        withContext(Dispatchers.Main.immediate) { }
        assertDisposed(worker)
    }

    @Test
    fun semanticsDiffCallback_appliesTreeOnMainThread() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        riveWorker.run {
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
            commandQueue.run {
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

    /** Delivery messages bypass synchronization barriers on API 22 and newer. */
    @Test
    fun automaticDelivery_marksMessagesAsyncWhenSupported() = runBlocking {
        val observed = CompletableDeferred<Boolean>()
        val worker = CommandQueue(RenderContextGL(), messageHandlerFactory = { callback ->
            Handler(Looper.getMainLooper()) { message ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    observed.complete(message.isAsynchronous)
                } else {
                    observed.complete(false)
                }
                callback.handleMessage(message)
            }
        })
        try {
            withTimeout(5_000) { loadFile(worker).close() }
            assertEquals(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1,
                withTimeout(5_000) { observed.await() }
            )
        } finally {
            worker.release("CommandQueueLifecycleTest", "Async message checked")
            assertDisposed(worker)
        }
    }

    /** A worker created off Main delivers replies without polling or a UI lifecycle. */
    @Test
    fun automaticDelivery_withoutPolling_deliversReplies() = runBlocking {
        val worker = CommandQueue()
        try {
            withContext(Dispatchers.Main.immediate) {
                withTimeout(5_000) { loadFile(worker).close() }
            }
        } finally {
            worker.release("CommandQueueLifecycleTest", "Automatic delivery complete")
            assertDisposed(worker)
        }
    }

    /** Unsolicited messages must wake Main even with no pending suspend requests. */
    @Test
    fun automaticDelivery_withoutPendingRequest_deliversDeletion() = runBlocking {
        val worker = CommandQueue()
        try {
            val resources = worker.loadRiveResources(R.raw.empty)
            val handle = resources.stateMachine.stateMachineHandle
            withContext(Dispatchers.Main.immediate) {
                worker.semanticTree(handle)
                assertTrue(worker.hasSemanticTree(handle))
                resources.close()
            }
            withTimeout(5_000) {
                while (withContext(Dispatchers.Main.immediate) { worker.hasSemanticTree(handle) }) {
                    delay(10)
                }
            }
        } finally {
            worker.release("CommandQueueLifecycleTest", "Deletion delivered")
            assertDisposed(worker)
        }
    }

    /** Pending Handler messages are harmless when final release occurs before Main can drain. */
    @Test
    fun release_withDeliveryPending_cancelsRequest() = runBlocking {
        val scheduled = CountDownLatch(1)
        val worker = CommandQueue(RenderContextGL(), messageHandlerFactory = { callback ->
            object : Handler(Looper.getMainLooper(), callback) {
                /** Signals only after a real message has been enqueued while Main is held. */
                override fun sendMessageAtTime(message: Message, uptimeMillis: Long): Boolean =
                    super.sendMessageAtTime(message, uptimeMillis).also { scheduled.countDown() }
            }
        })
        val bytes = context.resources.openRawResource(R.raw.multipleartboards).use {
            it.readBytes()
        }
        val result = CompletableDeferred<Result<FileHandle>>()
        val mainHeld = CountDownLatch(1)
        val releaseMain = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                result.complete(runCatching { worker.loadFile(bytes) })
            }
            mainHeld.countDown()
            releaseMain.await(5, TimeUnit.SECONDS)
        }
        try {
            assertTrue(mainHeld.await(2, TimeUnit.SECONDS))
            assertTrue(scheduled.await(2, TimeUnit.SECONDS))
            worker.release("CommandQueueLifecycleTest", "Released before delivery")
            assertTrue(worker.awaitShutdown(2_000))
        } finally {
            releaseMain.countDown()
        }
        assertTrue(withTimeout(5_000) { result.await() }.isFailure)
        withContext(Dispatchers.Main.immediate) { }
        assertDisposed(worker)
    }

    /** Loads a file without any test-owned polling. */
    private suspend fun loadFile(commandQueue: CommandQueue): RiveFile {
        val bytes = context.resources.openRawResource(R.raw.multipleartboards).use {
            it.readBytes()
        }
        return RiveFile.load(RiveFileSource.Bytes(bytes), commandQueue)
    }
}
