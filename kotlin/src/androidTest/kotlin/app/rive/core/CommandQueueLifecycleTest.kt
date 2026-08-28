package app.rive.core

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.Fit
import app.rive.RiveAndroidTest
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.runtime.kotlin.test.R
import app.rive.semantics.SemanticTreeModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class CommandQueueLifecycleTest : RiveAndroidTest() {
    @Test
    fun release_fromCallerThread_disposes() {
        val commandQueue = CommandQueue()

        commandQueue.release(
            "CommandQueueLifecycleTest",
            "Final release from caller thread"
        )

        assertDisposed(commandQueue)
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
                "Final release from background thread"
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
                    "Final release from command server thread"
                )
            } catch (t: Throwable) {
                thrown.set(t)
            } finally {
                releaseAttempted.countDown()
            }
        }

        assertTrue(
            releaseAttempted.await(2, TimeUnit.SECONDS),
            "Command server release callback did not run"
        )
        assertTrue(
            thrown.get() is IllegalStateException,
            "Expected command server release to throw IllegalStateException"
        )
        assertFalse(
            commandQueue.isDisposed,
            "CommandQueue should not be disposed after failed release attempt"
        )

        commandQueue.release(
            "CommandQueueLifecycleTest",
            "Final release from caller thread"
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
            "Polling loop did not request a frame"
        )
        // Release while we are in the middle of a frame tick, before polling. This would trigger an
        // exception when polling the released command queue if polling is incorrectly handling
        // disposal.
        commandQueue.release(
            "CommandQueueLifecycleTest",
            "Disposed while polling"
        )
        firstFrameMayContinue.complete(Unit)

        // Confirm that the polling loop does not request another frame after disposal, i.e. expect
        // a timeout.
        assertFalse(
            secondFrameRequested.await(250, TimeUnit.MILLISECONDS),
            "Polling requested another frame after the command queue was disposed"
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
            "Polling ticker did not run on the main thread"
        )

        withContext(Dispatchers.Main.immediate) {
            lifecycleOwner.currentState = Lifecycle.State.DESTROYED
        }
        withTimeout(2_000) { polling.await() }
        commandQueue.release(
            "CommandQueueLifecycleTest",
            "Final release after main-thread polling"
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
            "Final release after off-main poll"
        )
        assertDisposed(commandQueue)
    }

    @Test
    fun semanticsDiffCallback_appliesTreeOnMainThread() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.tabtest) {
                ViewModelInstance.fromFile(
                    file,
                    ViewModelSource.DefaultForArtboard(artboard).defaultInstance()
                ).use { viewModelInstance ->
                    riveWorker.bindViewModelInstance(
                        stateMachine.stateMachineHandle,
                        viewModelInstance.instanceHandle
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
                        start = CoroutineStart.UNDISPATCHED
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
                        surfaceHeight = 500f
                    )

                    assertTrue(
                        withTimeout(2_000) { appliedOnMain.await() },
                        "Semantic diff was not applied on the main thread"
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
                        "Native deletion callback did not remove the tree"
                    )
                }
            }
        } finally {
            commandQueue.release(
                "CommandQueueLifecycleTest",
                "Final release after state machine deletion"
            )
            assertDisposed(commandQueue)
        }
    }

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
