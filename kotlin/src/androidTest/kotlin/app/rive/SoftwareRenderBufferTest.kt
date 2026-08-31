package app.rive

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.core.withRiveResources
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
        val res = loadRiveResources(R.raw.empty)
        SoftwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            res.stateMachine.advance(0.milliseconds)
            val destination = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val rendered = buffer.renderInto(destination, res.artboard, res.stateMachine)
            assertSame(destination, rendered)
            assertEquals(Bitmap.Config.ARGB_8888, rendered.config)
        }
    }

    @Test
    fun renderInto_withClearColor_fillsBitmap() = runBlocking {
        val res = loadRiveResources(R.raw.empty)
        SoftwareRenderBuffer(16, 16, riveWorker).use { buffer ->
            val destination = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

            buffer.renderInto(
                destination,
                res.artboard,
                res.stateMachine,
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

    @Test
    fun renderInto_invalidBitmap_throws() = runBlocking<Unit> {
        val res = loadRiveResources(R.raw.empty)
        SoftwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            assertFailsWith<IllegalArgumentException>(
                "renderInto should throw on invalid bitmap shape"
            ) {
                buffer.renderInto(
                    Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888),
                    res.artboard,
                    res.stateMachine
                )
            }
        }
    }

    @Test
    fun renderInto_withClosedResource_throws() = runBlocking<Unit> {
        val res = loadRiveResources(R.raw.empty)
        res.artboard.close()

        SoftwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            val destination = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            try {
                assertFailsWith<RiveResourceClosedException> {
                    buffer.renderInto(destination, res.artboard, res.stateMachine)
                }
            } finally {
                destination.recycle()
            }
        }
    }

    @Test
    fun renderInto_withMismatchedResources_throws() = runBlocking<Unit> {
        val foreignWorker = RiveWorker()
        try {
            val owningRes = loadRiveResources(R.raw.empty)
            val siblingRes = loadRiveResources(R.raw.empty)
            foreignWorker.withPolling {
                foreignWorker.withRiveResources(R.raw.empty) {
                    SoftwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                        val destination = Bitmap.createBitmap(
                            64,
                            64,
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            assertFailsWith<RiveIncompatibleResourceException> {
                                buffer.renderInto(
                                    destination,
                                    artboard,
                                    owningRes.stateMachine
                                )
                            }
                            assertFailsWith<RiveIncompatibleResourceException> {
                                buffer.renderInto(
                                    destination,
                                    owningRes.artboard,
                                    stateMachine
                                )
                            }
                            assertFailsWith<RiveIncompatibleResourceException> {
                                buffer.renderInto(
                                    destination,
                                    owningRes.artboard,
                                    siblingRes.stateMachine
                                )
                            }
                        } finally {
                            destination.recycle()
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
