package app.rive.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveAndroidTest
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
            "Command-server release callback did not run"
        )
        assertTrue(
            thrown.get() is IllegalStateException,
            "Expected command-server release to throw IllegalStateException"
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
}
