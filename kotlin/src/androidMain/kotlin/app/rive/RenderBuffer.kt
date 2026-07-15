@file:Suppress("DEPRECATION")

package app.rive

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import app.rive.core.CheckableAutoCloseable
import app.rive.core.CloseOnce
import app.rive.core.RenderingDefaults
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
import app.rive.core.traceSection

/**
 * Deprecated software-only legacy render buffer.
 *
 * This type remains for source compatibility and keeps the historical software render->read API.
 * For new usage:
 * - Use [SoftwareRenderBuffer] for synchronous CPU-backed rendering.
 * - Use [HardwareRenderBuffer] for asynchronous GPU-backed rendering on API 29+.
 *
 * @see SoftwareRenderBuffer
 * @see HardwareRenderBuffer
 */
@Deprecated(
    message = "RenderBuffer is deprecated. Use SoftwareRenderBuffer for software rendering or HardwareRenderBuffer for hardware rendering.",
    replaceWith = ReplaceWith("SoftwareRenderBuffer(width, height, riveWorker)"),
    level = DeprecationLevel.WARNING
)
class RenderBuffer(
    val width: Int,
    val height: Int,
    private val riveWorker: RiveWorker
) : CheckableAutoCloseable {
    init {
        require(width > 0 && height > 0) { "RenderBuffer width/height must be > 0" }
    }

    /** Surface used for rendering and layout operations such as [Artboard.resizeArtboard]. */
    val surface: RiveSurface = riveWorker.createImageSurface(width, height)

    private val closer = CloseOnce("RenderBuffer") {
        surface.close()
    }
    override val closed: Boolean
        get() = closer.closed

    override fun close() = closer.close()

    /** RGBA bytes filled by native drawToBuffer calls. */
    private val pixels: ByteArray = ByteArray(width * height * 4)

    /** Scratch array reused for RGBA->ARGB conversion. */
    private val argbScratch by lazy(LazyThreadSafetyMode.NONE) { IntArray(width * height) }

    /**
     * Synchronously renders the artboard/state-machine into this software buffer.
     *
     * @throws IllegalArgumentException If [artboard] or [stateMachine] are not owned by this
     *    buffer's worker, or if [stateMachine] was not created from [artboard].
     * @throws IllegalStateException If this buffer's surface has been closed or the worker has
     *    been released.
     * @throws RiveDrawToBufferException If the native draw-to-buffer operation fails.
     */
    @Throws(
        IllegalArgumentException::class,
        IllegalStateException::class,
        RiveDrawToBufferException::class
    )
    fun render(
        artboard: Artboard,
        stateMachine: StateMachine,
        fit: Fit = RenderingDefaults.defaultFit(),
        clearColor: Int = RenderingDefaults.CLEAR_COLOR
    ): RenderBuffer {
        require(artboard.isOwnedBy(riveWorker)) {
            "RenderBuffer and Artboard must use the same RiveWorker"
        }
        require(stateMachine.isOwnedBy(riveWorker)) {
            "RenderBuffer and StateMachine must use the same RiveWorker"
        }
        require(stateMachine.isFromArtboard(artboard)) {
            "RenderBuffer StateMachine must be created from the supplied Artboard"
        }
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
        }
        return this
    }

    /**
     * Backward-compatible alias for [render].
     *
     * @throws IllegalArgumentException If [artboard] or [stateMachine] are not owned by this
     *    buffer's worker, or if [stateMachine] was not created from [artboard].
     * @throws IllegalStateException If this buffer's surface has been closed or the worker has
     *    been released.
     * @throws RiveDrawToBufferException If the native draw-to-buffer operation fails.
     * @see render
     */
    @Deprecated(
        message = "Use render(...) instead.",
        replaceWith = ReplaceWith("render(artboard, stateMachine, fit, clearColor)"),
        level = DeprecationLevel.WARNING
    )
    @Throws(
        IllegalArgumentException::class,
        IllegalStateException::class,
        RiveDrawToBufferException::class
    )
    fun snapshot(
        artboard: Artboard,
        stateMachine: StateMachine,
        fit: Fit = RenderingDefaults.defaultFit(),
        clearColor: Int = RenderingDefaults.CLEAR_COLOR
    ): RenderBuffer = render(artboard, stateMachine, fit, clearColor)

    /** Copies this buffer's latest rendered software pixels into [bitmap]. */
    fun copyInto(bitmap: Bitmap): Bitmap = traceSection("Rive/RenderBuffer/CopyInto") {
        require(
            bitmap.width == width &&
                    bitmap.height == height &&
                    bitmap.config == Bitmap.Config.ARGB_8888
        ) { "Bitmap must be ${width}x$height ARGB_8888" }

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
        bitmap
    }

    /** Returns a new ARGB_8888 bitmap containing the latest rendered software pixels. */
    fun toBitmap(): Bitmap = traceSection("Rive/RenderBuffer/ToBitmap") {
        copyInto(createBitmap(width, height, Bitmap.Config.ARGB_8888))
    }
}
