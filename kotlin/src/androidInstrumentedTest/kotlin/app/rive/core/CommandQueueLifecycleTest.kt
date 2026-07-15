package app.rive.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveAndroidTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
