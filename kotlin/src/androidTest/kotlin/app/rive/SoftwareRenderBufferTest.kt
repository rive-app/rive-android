package app.rive

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.core.RenderContextGL
import app.rive.core.RenderContextVulkan
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.core.withPolling
import app.rive.core.withRiveResources
import app.rive.runtime.kotlin.test.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

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

    /** Verifies OpenGL warmup can be followed by a single image-surface draw with visible pixels. */
    @Test
    fun precompileShaders_openGL_rendersFirstImageFrame() {
        assertPrecompiledFirstFrame(RenderBackend.OpenGL)
    }

    /** Verifies Vulkan warmup renders its first image frame without using OpenGL fallback. */
    @Test
    fun precompileShaders_vulkan_rendersFirstImageFrame() {
        assumeTrue("Vulkan requires Android API 29", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        assertPrecompiledFirstFrame(RenderBackend.Vulkan)
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

    /**
     * Creates a concrete backend with warmup enabled and checks its first rendered image.
     *
     * Only unsupported renderer initialization skips the test. Loading, rendering, pixel checks,
     * and teardown must succeed. No delay or retry hides an incomplete first frame.
     *
     * @param backend The concrete renderer to exercise without backend fallback.
     */
    private fun assertPrecompiledFirstFrame(backend: RenderBackend) {
        val worker = try {
            RiveWorker(
                renderContext = when (backend) {
                    RenderBackend.OpenGL -> RenderContextGL()
                    RenderBackend.Vulkan -> RenderContextVulkan()
                },
                precompileShaders = true,
            )
        } catch (unsupported: RiveInitializationException) {
            assumeTrue("$backend unavailable: ${unsupported.message}", false)
            return
        }
        try {
            worker.withPolling {
                runBlocking {
                    worker.withRiveResources(
                        R.raw.multipleartboards,
                        artboardName = "artboard1",
                        stateMachineName = "artboard1stateMachine1",
                    ) {
                        SoftwareRenderBuffer(64, 64, worker).use { buffer ->
                            val destination = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                            try {
                                stateMachine.advance(0.milliseconds)
                                buffer.renderInto(
                                    destination,
                                    artboard,
                                    stateMachine,
                                    clearColor = Color.TRANSPARENT,
                                )
                                val pixels = IntArray(destination.width * destination.height)
                                destination.getPixels(
                                    pixels,
                                    0,
                                    destination.width,
                                    0,
                                    0,
                                    destination.width,
                                    destination.height,
                                )
                                assertTrue(
                                    pixels.any { Color.alpha(it) > 0 },
                                    "$backend warmup produced a blank first frame",
                                )
                            } finally {
                                destination.recycle()
                            }
                        }
                    }
                }
            }
        } finally {
            worker.release(javaClass.simpleName, "Shader warmup smoke test cleanup")
            assertDisposed(worker)
        }
    }
}
