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
 * @param width Width in pixels.
 * @param height Height in pixels.
 * @param riveWorker Worker used for drawing.
 * @throws IllegalArgumentException If width or height are not positive.
 * @throws RiveResourceClosedException If the owning Rive worker has been disposed.
 * @throws RiveRenderException If the software render surface cannot be created.
 * @deprecated Use [SoftwareRenderBuffer] for software rendering or [HardwareRenderBuffer] for
 *    hardware rendering. This class will be removed in 12.0.
 * @see SoftwareRenderBuffer
 * @see HardwareRenderBuffer
 */
@Deprecated(
    message = "Use SoftwareRenderBuffer for software rendering or HardwareRenderBuffer for " +
        "hardware rendering. RenderBuffer will be removed in 12.0.",
    replaceWith = ReplaceWith("SoftwareRenderBuffer(width, height, riveWorker)"),
    level = DeprecationLevel.WARNING
)
class RenderBuffer @Throws(
    IllegalArgumentException::class,
    RiveResourceClosedException::class,
    RiveRenderException::class
) constructor(
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

    /** Closes this buffer and its owned render surface. */
    override fun close() = closer.close()

    /** RGBA bytes filled by native drawToBuffer calls. */
    private val pixels: ByteArray = ByteArray(width * height * 4)

    /** Scratch array reused for RGBA->ARGB conversion. */
    private val argbScratch by lazy(LazyThreadSafetyMode.NONE) { IntArray(width * height) }

    /**
     * Synchronously renders the artboard/state-machine into this software buffer.
     *
     * @throws RiveResourceClosedException If this buffer, its surface, [artboard], or
     *    [stateMachine] has been closed, or if the owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [artboard] or [stateMachine] are not owned by
     *    this buffer's worker, or if [stateMachine] was not created from [artboard].
     * @throws RiveDrawToBufferException If the native draw-to-buffer operation fails.
     */
    @Throws(
        RiveIncompatibleResourceException::class,
        RiveResourceClosedException::class,
        RiveDrawToBufferException::class
    )
    fun render(
        artboard: Artboard,
        stateMachine: StateMachine,
        fit: Fit = RenderingDefaults.defaultFit(),
        clearColor: Int = RenderingDefaults.CLEAR_COLOR
    ): RenderBuffer {
        closer.checkOpen()
        surface.checkOpen()
        artboard.checkOpen()
        stateMachine.checkOpen()
        artboard.requireOwnedBy(riveWorker)
        stateMachine.requireFromArtboard(artboard)
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
     * @throws RiveResourceClosedException If this buffer, its surface, [artboard], or
     *    [stateMachine] has been closed, or if the owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [artboard] or [stateMachine] are not owned by
     *    this buffer's worker, or if [stateMachine] was not created from [artboard].
     * @throws RiveDrawToBufferException If the native draw-to-buffer operation fails.
     * @deprecated Use [render]. This alias will be removed in 12.0.
     * @see render
     */
    @Deprecated(
        message = "Use render(...) instead. This alias will be removed in 12.0.",
        replaceWith = ReplaceWith("render(artboard, stateMachine, fit, clearColor)"),
        level = DeprecationLevel.WARNING
    )
    @Throws(
        RiveIncompatibleResourceException::class,
        RiveResourceClosedException::class,
        RiveDrawToBufferException::class
    )
    fun snapshot(
        artboard: Artboard,
        stateMachine: StateMachine,
        fit: Fit = RenderingDefaults.defaultFit(),
        clearColor: Int = RenderingDefaults.CLEAR_COLOR
    ): RenderBuffer = render(artboard, stateMachine, fit, clearColor)

    /**
     * Copies this buffer's latest rendered software pixels into [bitmap].
     *
     * @param bitmap Destination bitmap matching this buffer's dimensions in ARGB_8888 format.
     * @return The supplied [bitmap].
     * @throws RiveResourceClosedException If this buffer has been closed.
     * @throws IllegalArgumentException If [bitmap] has incompatible dimensions or format.
     */
    @Throws(IllegalArgumentException::class, RiveResourceClosedException::class)
    fun copyInto(bitmap: Bitmap): Bitmap {
        closer.checkOpen()
        return traceSection("Rive/RenderBuffer/CopyInto") {
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
    }

    /**
     * Returns a new ARGB_8888 bitmap containing the latest rendered software pixels.
     *
     * @return A bitmap containing this buffer's pixels.
     * @throws RiveResourceClosedException If this buffer has been closed.
     */
    @Throws(RiveResourceClosedException::class)
    fun toBitmap(): Bitmap {
        closer.checkOpen()
        return traceSection("Rive/RenderBuffer/ToBitmap") {
            copyInto(createBitmap(width, height, Bitmap.Config.ARGB_8888))
        }
    }
}
