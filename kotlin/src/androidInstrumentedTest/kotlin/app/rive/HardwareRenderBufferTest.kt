package app.rive

import android.graphics.Bitmap
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.core.withDefaultRiveResources
import app.rive.core.withPolling
import app.rive.test.R
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
        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.empty) {
                HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                    buffer.render(artboard, stateMachine)
                    assertEquals(Bitmap.Config.HARDWARE, buffer.consumeLatestBitmap()?.config)

                    buffer.close()

                    assertTrue(buffer.closed)
                    assertTrue(buffer.surface.closed)
                }
            }
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
        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.empty) {
                val buffer = HardwareRenderBuffer(64, 64, riveWorker)
                buffer.close()

                assertFailsWith<IllegalStateException> {
                    buffer.render(artboard, stateMachine)
                }
                assertFailsWith<IllegalStateException> {
                    buffer.consumeLatestBitmap()
                }
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun render_withMismatchedResources_throws() = runBlocking<Unit> {
        val foreignWorker = RiveWorker()
        try {
            riveWorker.withPolling {
                foreignWorker.withPolling {
                    riveWorker.withDefaultRiveResources(R.raw.empty) {
                        val owningArtboard = artboard
                        val owningStateMachine = stateMachine
                        riveWorker.withDefaultRiveResources(R.raw.empty) {
                            val siblingStateMachine = stateMachine
                            foreignWorker.withDefaultRiveResources(R.raw.empty) {
                                val foreignArtboard = artboard
                                val foreignStateMachine = stateMachine
                                HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                                    assertFailsWith<IllegalArgumentException> {
                                        buffer.render(foreignArtboard, owningStateMachine)
                                    }
                                    assertFailsWith<IllegalArgumentException> {
                                        buffer.render(owningArtboard, foreignStateMachine)
                                    }
                                    assertFailsWith<IllegalArgumentException> {
                                        buffer.render(owningArtboard, siblingStateMachine)
                                    }
                                }
                            }
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
        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.empty) {
                HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                    buffer.render(artboard, stateMachine)

                    assertEquals(Bitmap.Config.HARDWARE, buffer.consumeLatestBitmap()?.config)
                }
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun render_repeatedly_emitsAndConsumesHardwareBitmaps() = runBlocking {
        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.empty) {
                HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                    repeat(3) {
                        val frameAvailable = async(start = CoroutineStart.UNDISPATCHED) {
                            withTimeout(2_000L) { buffer.frameAvailable.first() }
                        }
                        buffer.render(artboard, stateMachine)
                        frameAvailable.await()

                        assertEquals(
                            Bitmap.Config.HARDWARE,
                            buffer.consumeLatestBitmap()?.config
                        )
                    }
                }
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun consumeLatestBitmap_returnsHardwareBitmap() = runBlocking {
        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.empty) {
                HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                    val frameAvailable = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(2_000L) { buffer.frameAvailable.first() }
                    }
                    buffer.render(artboard, stateMachine)
                    frameAvailable.await()

                    val bitmap = buffer.consumeLatestBitmap()
                    assertEquals(Bitmap.Config.HARDWARE, bitmap?.config)
                }
            }
        }
    }
}
