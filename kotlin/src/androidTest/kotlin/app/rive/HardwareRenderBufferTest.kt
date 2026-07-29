package app.rive

import android.graphics.Bitmap
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.core.withDefaultRiveResources
import app.rive.core.withPolling
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalHardwareBitmapRendering::class)
class HardwareRenderBufferTest : RiveAndroidTest() {
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun close_closesBufferAndSurface() {
        val buffer = HardwareRenderBuffer(64, 64, riveWorker)

        buffer.close()

        assertTrue(buffer.closed)
        assertTrue(buffer.surface.closed)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun close_isIdempotent() {
        val buffer = HardwareRenderBuffer(64, 64, riveWorker)

        buffer.close()
        buffer.close()

        assertTrue(buffer.closed)
        assertTrue(buffer.surface.closed)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun close_afterFramePublication_closesBufferAndSurface() = runBlocking {
        val res = loadDefaultRiveResources(R.raw.empty)
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            buffer.render(res.artboard, res.stateMachine)
            assertEquals(Bitmap.Config.HARDWARE, buffer.consumeLatestBitmap()?.config)

            buffer.close()

            assertTrue(buffer.closed)
            assertTrue(buffer.surface.closed)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun constructor_withZeroDimensions_throws() {
        assertFailsWith<IllegalArgumentException> {
            HardwareRenderBuffer(0, 64, riveWorker)
        }
        assertFailsWith<IllegalArgumentException> {
            HardwareRenderBuffer(64, 0, riveWorker)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun operations_afterClose_throw() = runBlocking<Unit> {
        val res = loadDefaultRiveResources(R.raw.empty)
        val buffer = HardwareRenderBuffer(64, 64, riveWorker)
        buffer.close()

        assertFailsWith<IllegalStateException> {
            buffer.render(res.artboard, res.stateMachine)
        }
        assertFailsWith<IllegalStateException> {
            buffer.consumeLatestBitmap()
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun render_withMismatchedResources_throws() = runBlocking<Unit> {
        val foreignWorker = RiveWorker()
        try {
            val owningRes = loadDefaultRiveResources(R.raw.empty)
            val siblingRes = loadDefaultRiveResources(R.raw.empty)
            foreignWorker.withPolling {
                foreignWorker.withDefaultRiveResources(R.raw.empty) {
                    HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                        assertFailsWith<IllegalArgumentException> {
                            buffer.render(artboard, owningRes.stateMachine)
                        }
                        assertFailsWith<IllegalArgumentException> {
                            buffer.render(owningRes.artboard, stateMachine)
                        }
                        assertFailsWith<IllegalArgumentException> {
                            buffer.render(owningRes.artboard, siblingRes.stateMachine)
                        }
                    }
                }
            }
        } finally {
            foreignWorker.release("HardwareRenderBufferTest", "Test cleanup")
            assertDisposed(foreignWorker)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun consumeLatestBitmap_beforeFirstFrame_returnsNull() {
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            assertEquals(null, buffer.consumeLatestBitmap())
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun render_firstFrame_canBeConsumedWithoutCollectingFrameAvailable() = runBlocking {
        val res = loadDefaultRiveResources(R.raw.empty)
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            buffer.render(res.artboard, res.stateMachine)

            assertEquals(Bitmap.Config.HARDWARE, buffer.consumeLatestBitmap()?.config)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun render_repeatedly_emitsAndConsumesHardwareBitmaps() = runBlocking {
        val res = loadDefaultRiveResources(R.raw.empty)
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            repeat(3) {
                val frameAvailable = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(2_000L) { buffer.frameAvailable.first() }
                }
                buffer.render(res.artboard, res.stateMachine)
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
    fun consumeLatestBitmap_returnsHardwareBitmap() = runBlocking {
        val res = loadDefaultRiveResources(R.raw.empty)
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            val frameAvailable = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000L) { buffer.frameAvailable.first() }
            }
            buffer.render(res.artboard, res.stateMachine)
            frameAvailable.await()

            val bitmap = buffer.consumeLatestBitmap()
            assertEquals(Bitmap.Config.HARDWARE, bitmap?.config)
        }
    }
}
