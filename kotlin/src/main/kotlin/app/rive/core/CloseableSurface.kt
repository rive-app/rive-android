package app.rive.core

import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.view.Surface

/**
 * An owned Android [Surface] paired with the Android resource that backs it. Used to pass surfaces
 * to [CommandQueue.createRiveSurface], which takes ownership, while also encoding resource cleanup.
 *
 * Once passed to [CommandQueue.createRiveSurface], the caller should not call [close] directly.
 * Rather, this surface will be wrapped and owned by a [RiveSurface] that calls close after
 * successful creation, or closed by [CommandQueue.createRiveSurface] if creation fails.
 *
 * After successful creation, resource cleanup is ordered asynchronously on the command queue so
 * that Android resources are not released until all pending commands that target the surface have
 * completed.
 */
interface CloseableSurface : AutoCloseable {
    /** Surface used to create the backend render target. */
    val surface: Surface

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
 * @param surfaceTexture Texture source backing the created [Surface].
 */
class SurfaceTextureSurface(
    private val surfaceTexture: SurfaceTexture
) : CloseableSurface {
    override val surface: Surface = Surface(surfaceTexture)

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

    private val closer = CloseOnce("ImageReaderSurface") {
        imageReader.close()
    }

    override fun close() = closer.close()
}
