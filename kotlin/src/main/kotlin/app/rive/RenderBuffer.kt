package app.rive

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import app.rive.core.CheckableAutoCloseable
import app.rive.core.CloseOnce
import app.rive.core.CommandQueue
import app.rive.core.RiveSurface
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit

/**
 * Represents the pixels produced by rendering a frame off-screen.
 *
 * Pixels are stored in RGBA byte order, top-left origin.
 *
 * This class must be [closed][close] when you no longer need it to free its resources. The buffer
 * creates and manages its own image surface, which is closed when the buffer is closed.
 *
 * The buffer is initially empty; use [snapshot] to fill it with rendered content, then access the
 * image as a bitmap through [toBitmap] or [copyInto].
 *
 * @param width The width of the buffer in pixels.
 * @param height The height of the buffer in pixels.
 * @param commandQueue The command queue used to create the underlying image surface.
 * @throws IllegalArgumentException if [width] or [height] are not greater than zero.
 */
class RenderBuffer(
    val width: Int,
    val height: Int,
    commandQueue: CommandQueue
) : CheckableAutoCloseable {
    init {
        require(width > 0 && height > 0) { "RenderBuffer width/height must be > 0" }
    }

    private val closer = CloseOnce("RenderBuffer", { surface.close() })
    override val closed = closer.closed
    override fun close() = closer.close()

    /** The underlying image surface for rendering. */
    private val surface: RiveSurface = commandQueue.createImageSurface(width, height)

    /** The pixel data in RGBA byte order, top-left origin. */
    private val pixels: ByteArray = ByteArray(width * height * 4)

    /** Scratch array for ARGB conversion. */
    private val argbScratch by lazy(LazyThreadSafetyMode.NONE) { IntArray(width * height) }

    /**
     * Fills the buffer with rendered content from the given artboard and state machine.
     *
     * The user is responsible for advancing the state machine to the desired time before calling
     * this method.
     *
     * @param artboard The artboard to snapshot.
     * @param stateMachine The state machine to snapshot.
     * @param fit The fit mode to use when rendering. Defaults to
     *    [app.rive.runtime.kotlin.core.Fit.CONTAIN].
     * @param alignment The alignment to use when rendering. Defaults to
     *    [app.rive.runtime.kotlin.core.Alignment.CENTER].
     * @param clearColor The background color to use. Defaults to transparent.
     * @return This buffer instance for method chaining.
     */
    fun snapshot(
        artboard: Artboard,
        stateMachine: StateMachine,
        fit: Fit = Fit.CONTAIN,
        alignment: Alignment = Alignment.CENTER,
        clearColor: Int = Color.TRANSPARENT
    ): RenderBuffer {
        artboard.commandQueue.drawToBuffer(
            artboard.artboardHandle,
            stateMachine.stateMachineHandle,
            surface,
            pixels,
            width,
            height,
            fit,
            alignment,
            clearColor
        )
        return this
    }

    /**
     * Fill an existing bitmap with the contents of the buffer. The supplied bitmap must match the
     * existing width, height, and ARGB_8888 format.
     *
     * @param bitmap The bitmap to fill.
     * @return The same filled bitmap.
     * @throws IllegalArgumentException if the bitmap does not match the buffer's dimensions or
     *    format.
     */
    @Throws(IllegalArgumentException::class)
    fun copyInto(bitmap: Bitmap): Bitmap {
        require(
            bitmap.width == width &&
                    bitmap.height == height &&
                    bitmap.config == Bitmap.Config.ARGB_8888
        ) { "Bitmap must be ${width}x$height ARGB_8888" }

        val argb = argbScratch
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
        bitmap.setPixels(argb, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Converts the buffer into an [Bitmap.Config.ARGB_8888] bitmap.
     *
     * If you already have a bitmap to reuse, consider using [copyInto] instead to avoid
     * allocations.
     *
     * @return A new bitmap with the contents of the buffer.
     * @see copyInto
     */
    fun toBitmap(): Bitmap =
        copyInto(createBitmap(width, height, Bitmap.Config.ARGB_8888))
}
