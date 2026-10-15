package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveImagePropertyDeadlockTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

    // 1x1 black pixel PNG.
    @OptIn(ExperimentalEncodingApi::class)
    @Suppress("SpellCheckingInspection")
    private val imageBytes = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABAQAAAAA3bvkkAAAACklEQVR4AWNgAAAAAgABc3UBGAAAAABJRU5ErkJggg=="
    )

    /**
     * Replacing an image whose texture is still queued for creation must not wait for the worker
     * while the file lock is held. A frame queued ahead of that creation job waits for the same
     * lock, so the wait would never end.
     */
    @Test
    fun replacingFreshImageWhileFrameWaitsForFileLockDoesNotDeadlock() {
        assumeTrue("Needs the Rive renderer", NativeRendererTestHelper.cppIsRiveRendererSupported())
        val bytes = appContext.resources.openRawResource(R.raw.data_bind_test_impl).readBytes()
        val file = File(bytes)
        try {
            val vmi = file.getViewModelByName("Test All").createInstanceFromName("Test Default")
            val imageProperty = vmi.getImageProperty("Test Image")

            // Counted down once the frame job may continue: either it got the file lock, or the
            // test gave up on the setter and unblocks it so the process can continue.
            val frameMayFinish = CountDownLatch(1)
            val frameStarted = CountDownLatch(1)
            val setterFinished = CountDownLatch(1)
            val setterFailure = AtomicReference<Throwable?>()

            val setter = thread(name = "image-setter") {
                try {
                    synchronized(file.fileLock) {
                        // Stand-in for a frame (advance/draw) that is running on the worker and
                        // needs the file lock. Everything queued on the worker after it waits
                        // behind it.
                        val queued = NativeWorkerTestHelper.cppRunOnRiveWorker {
                            frameStarted.countDown()
                            // A helper thread takes the lock so the job depends on it like a
                            // frame does, while the test can still unblock the job on timeout.
                            thread(name = "frame-lock") {
                                synchronized(file.fileLock) {}
                                frameMayFinish.countDown()
                            }
                            frameMayFinish.await(30, TimeUnit.SECONDS)
                        }
                        assertTrue(queued, "Frame job not queued")
                        assertTrue(frameStarted.await(5, TimeUnit.SECONDS), "Frame job never ran")

                        // The image's texture creation job is queued behind the frame.
                        val first = RiveRenderImage.fromEncoded(imageBytes, RendererType.Rive)
                        imageProperty.set(first)
                        first.release()

                        val second = RiveRenderImage.fromEncoded(imageBytes, RendererType.Rive)
                        // Drops the last reference to `first`, destroying it under the file lock.
                        imageProperty.set(second)
                        second.release()
                    }
                } catch (t: Throwable) {
                    setterFailure.set(t)
                } finally {
                    setterFinished.countDown()
                }
            }

            // Longer than the setter's own waits, so a slow worker isn't reported as a deadlock.
            val finished = setterFinished.await(15, TimeUnit.SECONDS)
            // Captured before unblocking, to show where the setter is stuck.
            val setterStack = setter.stackTrace.take(8).joinToString("\n  at ")
            if (!finished) {
                // Unblock the worker so the setter, and the rest of the test run, can continue.
                frameMayFinish.countDown()
            }
            setter.join(TimeUnit.SECONDS.toMillis(10))

            assertTrue(
                finished,
                "ViewModelImageProperty.set deadlocked with a frame waiting for the file lock:" +
                    "\n  at $setterStack"
            )
            assertNull(setterFailure.get())
            assertTrue(
                frameMayFinish.await(5, TimeUnit.SECONDS),
                "Frame job never got the file lock"
            )

            // The creation job drops the last reference to the first image, so the image is
            // destroyed on the worker. Queue from another thread: if the worker is stuck holding
            // its queue mutex, queuing blocks too.
            val workerRan = CountDownLatch(1)
            thread(name = "worker-probe", isDaemon = true) {
                NativeWorkerTestHelper.cppRunOnRiveWorker { workerRan.countDown() }
            }
            assertTrue(
                workerRan.await(5, TimeUnit.SECONDS),
                "Rive worker stopped running jobs after destroying the image"
            )
        } finally {
            // Also releases the view model instance.
            file.release()
        }
    }
}
