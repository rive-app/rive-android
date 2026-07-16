package app.rive.core

import app.rive.RiveRenderException

/**
 * Create a Rive rendering surface for Rive to draw into.
 *
 * ⚠️ The returned surface must be [closed][RiveSurface.close] when no longer needed.
 *
 * Uses the concrete render context to create an appropriate surface for the backend.
 *
 * @param surface Owned Android surface source. This command queue takes ownership and closes it
 *    if surface creation fails or when the created [RiveSurface] is destroyed on the command
 *    server thread.
 * @return A [RiveSurface] that can be used for rendering.
 * @throws RiveRenderException If the underlying Android or backend surface cannot be created.
 * @throws IllegalStateException If this command queue has been released.
 */
@Throws(RiveRenderException::class, IllegalStateException::class)
fun CommandQueue.createRiveSurface(
    surface: CloseableSurface
): RiveSurface {
    val androidRenderContext = renderContext as? AndroidRenderContext
        ?: throw RiveRenderException(
            "This command queue's render context cannot create window surfaces"
        )
    return try {
        androidRenderContext.createSurface(surface, nextDrawKey(), this)
    } catch (e: Throwable) {
        // Do not leak the Android resources if surface creation fails.
        surface.close()
        throw e
    }
}
