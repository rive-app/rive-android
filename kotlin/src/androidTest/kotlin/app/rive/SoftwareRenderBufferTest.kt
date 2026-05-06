package app.rive

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.core.withDefaultRiveResources
import app.rive.core.withPolling
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class SoftwareRenderBufferTest : RiveAndroidTest() {
    @Test
    fun renderInto_writesArgb8888() = runBlocking {
        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.empty) {
                SoftwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                    stateMachine.advance(0.milliseconds)
                    val destination = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                    val rendered = buffer.renderInto(destination, artboard, stateMachine)
                    assertSame(destination, rendered)
                    assertEquals(Bitmap.Config.ARGB_8888, rendered.config)
                }
            }
        }
    }

    @Test
    fun renderInto_withClearColor_fillsBitmap() = runBlocking {
        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.empty) {
                SoftwareRenderBuffer(16, 16, riveWorker).use { buffer ->
                    val destination = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

                    buffer.renderInto(
                        destination,
                        artboard,
                        stateMachine,
                        clearColor = Color.RED
                    )

                    val pixels = IntArray(destination.width * destination.height)
                    destination.getPixels(
                        pixels,
                        0,
                        destination.width,
                        0,
                        0,
                        destination.width,
                        destination.height
                    )
                    pixels.forEach { pixel ->
                        assertEquals(Color.RED, pixel)
                    }
                }
            }
        }
    }

    @Test
    fun renderInto_invalidBitmap_throws() = runBlocking<Unit> {
        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.empty) {
                SoftwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                    assertFailsWith<IllegalArgumentException>(
                        "renderInto should throw on invalid bitmap shape"
                    ) {
                        buffer.renderInto(
                            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888),
                            artboard,
                            stateMachine
                        )
                    }
                }
            }
        }
    }

    @Test
    fun renderInto_withMismatchedResources_throws() = runBlocking<Unit> {
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
                                SoftwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                                    val destination = Bitmap.createBitmap(
                                        64,
                                        64,
                                        Bitmap.Config.ARGB_8888
                                    )
                                    try {
                                        assertFailsWith<IllegalArgumentException> {
                                            buffer.renderInto(
                                                destination,
                                                foreignArtboard,
                                                owningStateMachine
                                            )
                                        }
                                        assertFailsWith<IllegalArgumentException> {
                                            buffer.renderInto(
                                                destination,
                                                owningArtboard,
                                                foreignStateMachine
                                            )
                                        }
                                        assertFailsWith<IllegalArgumentException> {
                                            buffer.renderInto(
                                                destination,
                                                owningArtboard,
                                                siblingStateMachine
                                            )
                                        }
                                    } finally {
                                        destination.recycle()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            foreignWorker.release("SoftwareRenderBufferTest", "Test cleanup")
            assertDisposed(foreignWorker)
        }
    }
}
