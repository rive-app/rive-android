package app.rive

import android.graphics.Bitmap
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalHardwareBitmapRendering::class)
class HardwareRenderBufferTest : RiveAndroidTest() {
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun close_afterFramePublication_closesBufferAndSurface() = runBlocking {
        val resources = loadRiveResources(R.raw.empty)
        val threadsBeforeConstruction = liveImageReaderThreads()
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            buffer.render(resources.artboard, resources.stateMachine)
            assertEquals(Bitmap.Config.HARDWARE, buffer.consumeLatestBitmap()?.config)

            buffer.close()

            assertTrue(buffer.closed)
            assertTrue(buffer.surface.closed)
            assertEquals(threadsBeforeConstruction, liveImageReaderThreads())
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun constructor_whenSurfaceCreationFails_stopsImageReaderThread() {
        val disposedWorker = RiveWorker()
        disposedWorker.release(javaClass.simpleName, "Force surface creation failure")
        assertDisposed(disposedWorker)
        val threadsBeforeConstruction = liveImageReaderThreads()

        assertFailsWith<RiveResourceClosedException> {
            HardwareRenderBuffer(64, 64, disposedWorker)
        }

        val threadsAfterFailure = liveImageReaderThreads()
        assertEquals(threadsBeforeConstruction, threadsAfterFailure)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun render_repeatedly_emitsAndConsumesHardwareBitmaps() = runBlocking {
        val resources = loadRiveResources(R.raw.empty)
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            repeat(3) {
                val frameAvailable = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(2_000L) { buffer.frameAvailable.first() }
                }
                buffer.render(resources.artboard, resources.stateMachine)
                frameAvailable.await()

                assertEquals(
                    Bitmap.Config.HARDWARE,
                    buffer.consumeLatestBitmap()?.config
                )
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun close_fromFrameCallbackThread_doesNotJoinItself() = runBlocking {
        val resources = loadRiveResources(R.raw.empty)
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            val callbackThread = CompletableDeferred<Thread>()
            val closeResult = CompletableDeferred<kotlin.Result<Unit>>()
            val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                buffer.frameAvailable.first()
                callbackThread.complete(Thread.currentThread())
                closeResult.complete(runCatching { buffer.close() })
            }

            buffer.render(resources.artboard, resources.stateMachine)

            withTimeout(750L) { closeResult.await() }.getOrThrow()
            val imageReaderThread = callbackThread.await()
            assertEquals("Rive/ImageReader", imageReaderThread.name)
            collector.join()
            withTimeout(2_000L) {
                while (imageReaderThread.isAlive) {
                    delay(10L)
                }
            }
            assertFalse(imageReaderThread.isAlive)
        }
    }

    /** Returns the live callback threads created by [HardwareRenderBuffer]. */
    private fun liveImageReaderThreads(): Set<Thread> =
        Thread.getAllStackTraces().keys.filterTo(mutableSetOf()) { thread ->
            thread.isAlive && thread.name == "Rive/ImageReader"
        }
}
