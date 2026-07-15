package app.rive.core

import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.view.Surface

/**
 * An owned Android [Surface] paired with the Android resource that backs it. Used only to create a
 * [RiveSurface] with [CommandQueue.createRiveSurface], which takes ownership, while also encoding
 * resource cleanup.
 *
 * Once passed to [CommandQueue.createRiveSurface], the caller should not call [close] directly.
 * Rather, this surface will be wrapped and owned by a [RiveSurface] that calls close after
 * successful creation, or closed by [CommandQueue.createRiveSurface] if creation fails.
 *
 * [width] and [height] are creation-time dimensions in physical pixels. They are not live
 * properties of the backing Android resource after [CommandQueue.createRiveSurface] returns.
 * Mutable surfaces, such as [SurfaceTextureSurface], report later size changes through
 * [RiveSurface.resize] instead of mutating this wrapper.
 *
 * [resizable] indicates whether the Android backing resource supports in-place size changes. The
 * created [RiveSurface] copies this value at construction and uses it to decide whether
 * [RiveSurface.resize] is valid.
 *
 * After successful creation, resource cleanup is ordered asynchronously on the command queue so
 * that Android resources are not released until all pending commands that target the surface have
 * completed.
 */
interface CloseableSurface : AutoCloseable {
    /** Surface used to create the backend render target. */
    val surface: Surface

    /** Creation-time width in physical pixels. */
    val width: Int

    /** Creation-time height in physical pixels. */
    val height: Int

    /** Whether the backing Android resource supports in-place size changes. */
    val resizable: Boolean

    /**
     * Releases the Android resources backing [surface].
     *
     * Callers normally should not call this directly after passing the instance to
     * [CommandQueue.createRiveSurface]. Implementations must be idempotent and tolerate being
     * closed by [CommandQueue.createRiveSurface] on creation failure, or later on the command
     * server thread when the wrapping [RiveSurface] is closed.
     */
    override fun close()
}

/**
 * [CloseableSurface] backed by a [SurfaceTexture].
 *
 * This owns both the temporary [Surface] wrapper and the [SurfaceTexture]. Both are intentionally
 * retained until Rive surface destruction so there is a single teardown point ordered by the
 * command queue.
 *
 * [width] and [height] capture the creation-time size. If the [SurfaceTexture] later changes size,
 * update the created [RiveSurface] with [RiveSurface.resize] instead of reusing this wrapper as a
 * source of current dimensions.
 *
 * @param surfaceTexture Texture source backing the created [Surface].
 * @param width Creation-time width in physical pixels.
 * @param height Creation-time height in physical pixels.
 * @throws IllegalArgumentException If [width] or [height] is not positive.
 */
class SurfaceTextureSurface(
    private val surfaceTexture: SurfaceTexture,
    override val width: Int,
    override val height: Int,
) : CloseableSurface {
    init {
        require(width > 0 && height > 0) {
            "SurfaceTextureSurface requires a positive width and height."
        }
    }

    override val surface: Surface = Surface(surfaceTexture)
    override val resizable: Boolean = true

    private val closer = CloseOnce("SurfaceTextureSurface") {
        // Technically this wrapper Surface could have been released immediately after creating the
        // RiveSurface, but it is simpler to keep it around until teardown.
        // The SurfaceTexture is the more important resource to retain until teardown.
        surface.release()
        surfaceTexture.release()
    }

    override fun close() = closer.close()
}

/**
 * [CloseableSurface] backed by an [ImageReader].
 *
 * The [Surface] is owned by the [ImageReader], so closing this wrapper closes only the reader. That
 * invalidates the surface after Rive has finished all queued work that targets it.
 *
 * @param imageReader Reader that owns the render target surface.
 */
class ImageReaderSurface(
    private val imageReader: ImageReader
) : CloseableSurface {
    override val surface: Surface = imageReader.surface
    override val width: Int = imageReader.width
    override val height: Int = imageReader.height
    override val resizable: Boolean = false

    init {
        require(width > 0 && height > 0) {
            "ImageReaderSurface requires a positive width and height."
        }
    }

    private val closer = CloseOnce("ImageReaderSurface") {
        imageReader.close()
    }

    override fun close() = closer.close()
}
