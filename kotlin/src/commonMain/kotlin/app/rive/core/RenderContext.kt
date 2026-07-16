package app.rive.core

import app.rive.RenderBackend
import app.rive.RiveRenderException

/**
 * A backend agnostic rendering context base class. Implementers contain the necessary state for
 * Rive to render, both in Kotlin and with associated native objects.
 *
 * Meant for use with a single [CommandQueue].
 *
 * ⚠️ As it contains native resources, it implements [CheckableAutoCloseable] and should be
 * [closed][CheckableAutoCloseable.close] when no longer needed, unless it is passed to a
 * [CommandQueue], in which case it assumes ownership of this object and will close it when it is
 * disposed.
 */
internal abstract class RenderContext : CheckableAutoCloseable {
    /** The native pointer to the backend-specific RenderContext object. */
    abstract val nativeObjectPointer: Long

    private val closer = CloseOnce("RenderContext") {
        dispose()
    }

    /** Disposes backend-specific resources. */
    protected abstract fun dispose()

    override fun close() = closer.close()
    override val closed: Boolean
        get() = closer.closed

    /**
     * Creates an off-screen [RiveSurface] that renders into a pixel buffer instead of a window
     * surface. This surface can be used to capture rendered output for tasks such as snapshot
     * testing, or to blit frames where no window surface exists.
     *
     * ⚠️ The returned [RiveSurface] must be [closed][RiveSurface.close] when no longer needed.
     *
     * @param width The width of the surface in pixels.
     * @param height The height of the surface in pixels.
     * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
     * @param commandQueue The owning command queue. The created surface acquires a reference so it
     *    can later schedule ordered disposal.
     * @return The created [RiveSurface].
     * @throws IllegalArgumentException If the requested dimensions are invalid.
     * @throws RiveRenderException If the backend cannot create an off-screen surface.
     * @throws IllegalStateException If the surface cannot acquire the command queue.
     */
    internal abstract fun createImageSurface(
        width: Int,
        height: Int,
        drawKey: DrawKey,
        commandQueue: CommandQueue
    ): RiveSurface
}

/** Creates the platform's JNI/native bridge, loading the native library if necessary. */
internal expect fun createPlatformBridge(): CommandQueueBridge

/**
 * Creates a platform render context for the requested backend.
 *
 * @throws app.rive.RiveInitializationException If the platform has no Rive runtime or the backend
 *    cannot be initialized.
 */
internal expect fun createPlatformRenderContext(renderBackend: RenderBackend): RenderContext
