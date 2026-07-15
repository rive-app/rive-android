package app.rive

import android.graphics.Bitmap
import app.rive.core.CheckableAutoCloseable
import app.rive.core.CloseOnce
import app.rive.core.RenderingDefaults
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
import app.rive.core.traceSection

/**
 * CPU-backed offscreen render target for snapshot-style rendering.
 *
 * Use this when you need software pixels, such as snapshot testing or CPU-side image processing.
 * For realtime presentation on hardware-accelerated canvases, prefer [HardwareRenderBuffer].
 *
 * The dimensions of this buffer are fixed at construction and cannot be resized. To render at a
 * different size, create a new buffer.
 *
 * Ownership/lifecycle:
 * - This class owns its internal render surface and must be [closed][close].
 * - Callers own destination bitmaps passed to [renderInto]; reuse a destination bitmap per size for
 *   repeated renders.
 * - Callers should recycle destination bitmaps when they are no longer needed.
 *
 * Performance:
 * - Rendering is synchronous and includes CPU pixel conversion.
 * - This is typically slower than [HardwareRenderBuffer] for continuous realtime rendering.
 *
 * Threading:
 * - [renderInto] is synchronous with respect to buffer population.
 *
 * API level:
 * - Available on all Android API levels supported by this runtime.
 *
 * @param width Width in pixels.
 * @param height Height in pixels.
 * @param riveWorker Worker used for drawing.
 * @throws IllegalArgumentException if width or height are not > 0.
 */
class SoftwareRenderBuffer(
    val width: Int,
    val height: Int,
    private val riveWorker: RiveWorker
) : CheckableAutoCloseable {
    init {
        require(width > 0 && height > 0) { "SoftwareRenderBuffer width/height must be > 0" }
    }

    /** Surface used for rendering and layout operations such as [Artboard.resizeArtboard]. */
    val surface: RiveSurface = riveWorker.createImageSurface(width, height)

    private val closer = CloseOnce("SoftwareRenderBuffer") {
        surface.close()
    }
    override val closed: Boolean
        get() = closer.closed

    override fun close() = closer.close()

    /** RGBA byte pixels produced by the native draw call. */
    private val pixels = ByteArray(width * height * 4)

    /** Scratch ARGB conversion storage reused across frames. */
    private val argbScratch by lazy(LazyThreadSafetyMode.NONE) { IntArray(width * height) }

    /**
     * Synchronously renders into [bitmap].
     *
     * @param bitmap Destination bitmap. Must match this buffer size and use ARGB_8888.
     * @param artboard Artboard to render.
     * @param stateMachine State machine to render.
     * @param fit Fit mode to use while rendering.
     * @param clearColor Clear color used before drawing.
     * @return The same [bitmap] instance after rendering.
     * @throws IllegalArgumentException If [bitmap] does not match this buffer's size/config, if
     *    [artboard] or [stateMachine] are not owned by this buffer's worker, or if [stateMachine]
     *    was not created from [artboard].
     * @throws IllegalStateException If this buffer's surface has been closed or the worker has
     *    been released.
     * @throws RiveDrawToBufferException If the native draw-to-buffer operation fails.
     */
    @Throws(
        IllegalArgumentException::class,
        IllegalStateException::class,
        RiveDrawToBufferException::class
    )
    fun renderInto(
        bitmap: Bitmap,
        artboard: Artboard,
        stateMachine: StateMachine,
        fit: Fit = RenderingDefaults.defaultFit(),
        clearColor: Int = RenderingDefaults.CLEAR_COLOR
    ): Bitmap {
        require(artboard.isOwnedBy(riveWorker)) {
            "SoftwareRenderBuffer and Artboard must use the same RiveWorker"
        }
        require(stateMachine.isOwnedBy(riveWorker)) {
            "SoftwareRenderBuffer and StateMachine must use the same RiveWorker"
        }
        require(stateMachine.isFromArtboard(artboard)) {
            "SoftwareRenderBuffer StateMachine must be created from the supplied Artboard"
        }
        require(
            bitmap.width == width &&
                    bitmap.height == height &&
                    bitmap.config == Bitmap.Config.ARGB_8888
        ) { "Bitmap must be ${width}x$height ARGB_8888" }

        traceSection("Rive/RenderBuffer/Render") {
            traceSection("Rive/RenderBuffer/Software/DrawToBuffer") {
                artboard.riveWorker.drawToBuffer(
                    artboard.artboardHandle,
                    stateMachine.stateMachineHandle,
                    surface,
                    pixels,
                    width,
                    height,
                    fit,
                    clearColor
                )
            }
            convertRgbaToArgbAndWrite(bitmap)
        }
        return bitmap
    }

    private fun convertRgbaToArgbAndWrite(bitmap: Bitmap) {
        val argb = argbScratch
        traceSection("Rive/RenderBuffer/Software/ConvertRgbaToArgb") {
            var i = 0
            var pixel = 0
            while (i < pixels.size) {
                val r = pixels[i].toInt() and 0xFF
                val g = pixels[i + 1].toInt() and 0xFF
                val b = pixels[i + 2].toInt() and 0xFF
                val a = pixels[i + 3].toInt() and 0xFF
                argb[pixel++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                i += 4
            }
        }
        traceSection("Rive/RenderBuffer/Software/SetPixels") {
            bitmap.setPixels(argb, 0, width, 0, 0, width, height)
        }
    }
}
