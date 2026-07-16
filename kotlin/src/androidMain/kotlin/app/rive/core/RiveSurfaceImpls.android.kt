package app.rive.core

import android.opengl.EGL14
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface
import androidx.annotation.WorkerThread
import app.rive.RiveLog
import app.rive.RiveRenderException
import app.rive.RiveShutdownException

/**
 * A collection of three surface properties needed for rendering.
 * - An EGLSurface, created from an Android Surface
 * - An opaque native GL surface resource
 * - A draw key, which uniquely identifies draw operations in the CommandQueue
 *
 * Meant for use with and created from a [RenderContextGL].
 *
 * ⚠️ This class assumes ownership of all resources and should be [closed][RiveSurface.close] when
 * no longer needed.
 *
 * @param eglSurface The EGLSurface created from the Android Surface.
 * @param display The EGLDisplay used to create the EGLSurface, used for destroying it.
 * @param closeableSurface Android surface source closed after the EGL surface is destroyed.
 * @param commandQueue Command queue that owns this surface and performs ordered disposal.
 * @param nativeSurfacePointer Opaque native GL surface resource owned by this surface.
 * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
 * @param width The width of the surface in pixels.
 * @param height The height of the surface in pixels.
 * @param resizable Whether the backing Android surface can resize in place.
 */
internal class RiveSurfaceGL(
    private val eglSurface: EGLSurface,
    private val display: EGLDisplay,
    private val closeableSurface: CloseableSurface,
    commandQueue: CommandQueue,
    nativeSurfacePointer: Long,
    drawKey: DrawKey,
    width: Int,
    height: Int,
    resizable: Boolean
) : RiveSurface(
    commandQueue,
    nativeSurfacePointer,
    drawKey,
    width,
    height,
    resizable
), AutoCloseable {
    companion object {
        const val TAG = "Rive/SurfaceGL"
    }

    /**
     * Destroys the EGLSurface and calls the super class to dispose of its resources.
     *
     * Runs on the command server thread. See the note in [close].
     *
     * @throws RiveShutdownException If unable to destroy the EGL surface.
     */
    @WorkerThread
    override fun dispose() {
        // Destroy the EGL surface first...
        RiveLog.d(TAG) { "Destroying EGL surface" }
        val destroyed = EGL14.eglDestroySurface(display, eglSurface)
        if (!destroyed) {
            throw RiveShutdownException("Unable to destroy EGL surface")
        }

        // ... Then release the Android surface source that backed the EGL surface ...
        closeableSurface.close()

        // ... Then dispose of base class resources
        super.dispose()
    }

}

/**
 * A PBuffer-backed EGL surface used for off-screen rendering and image capture.
 *
 * Meant for use with and created from a [RenderContextGL].
 *
 * ⚠️ This class assumes ownership of all resources and should be [closed][RiveSurface.close] when
 * no longer needed.
 */
internal class RiveSurfaceGLPBuffer(
    private val eglSurface: EGLSurface,
    private val display: EGLDisplay,
    commandQueue: CommandQueue,
    nativeSurfacePointer: Long,
    drawKey: DrawKey,
    width: Int,
    height: Int
) : RiveSurface(
    commandQueue,
    nativeSurfacePointer,
    drawKey,
    width,
    height,
    resizable = false
), AutoCloseable {
    companion object {
        const val TAG = "Rive/SurfaceGLPBuffer"
    }

    /**
     * Destroys the EGLSurface and calls the super class to dispose of other resources.
     *
     * Runs on the command server thread. See the note in [RiveSurface.close].
     *
     * @throws RiveShutdownException If unable to destroy the EGL surface.
     */
    @WorkerThread
    override fun dispose() {
        // Destroy the EGL PBuffer surface first...
        RiveLog.d(TAG) { "Destroying EGL PBuffer surface" }
        val destroyed = EGL14.eglDestroySurface(display, eglSurface)
        if (!destroyed) {
            throw RiveShutdownException("Unable to destroy EGL PBuffer surface")
        }

        // ... Then dispose of other resources
        super.dispose()
    }
}

/**
 * Vulkan-backed Android window surface.
 *
 * @param nativeSurfacePointer Native Vulkan surface wrapper pointer.
 * @param closeableSurface Android surface source closed after native Vulkan resources are released.
 * @param commandQueue Command queue that owns ordered disposal.
 * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
 * @param width The width of the surface in pixels.
 * @param height The height of the surface in pixels.
 * @param resizable Whether the backing Android surface can resize in place.
 */
internal class RiveSurfaceVulkan(
    nativeSurfacePointer: Long,
    private val closeableSurface: CloseableSurface,
    commandQueue: CommandQueue,
    drawKey: DrawKey,
    width: Int,
    height: Int,
    resizable: Boolean
) : RiveSurface(
    commandQueue,
    nativeSurfacePointer,
    drawKey,
    width,
    height,
    resizable
), AutoCloseable {
    companion object {
        const val TAG = "Rive/SurfaceVulkan"

        @JvmStatic
        private external fun cppCreateSurface(
            renderContextPointer: Long,
            surface: Surface,
            width: Int,
            height: Int
        ): Long

        /**
         * Creates a Vulkan-backed [RiveSurface] from an Android [CloseableSurface].
         *
         * The returned surface owns both the native Vulkan surface wrapper and the Android surface
         * source. Both are released through ordered command queue disposal when the returned
         * [RiveSurfaceVulkan] is closed.
         *
         * @param renderContext Vulkan render context used to create the native surface wrapper.
         * @param surface Owned Android surface source to render against.
         * @param commandQueue Command queue that owns ordered disposal.
         * @param drawKey The key used to uniquely identify draw operations in the CommandQueue.
         * @return The created [RiveSurfaceVulkan].
         * @throws RiveRenderException If the Android surface is invalid or native surface creation
         *    fails.
         * @throws IllegalStateException If the surface cannot acquire the command queue.
         */
        @Throws(RiveRenderException::class, IllegalStateException::class)
        internal fun create(
            renderContext: RenderContextVulkan,
            surface: CloseableSurface,
            commandQueue: CommandQueue,
            drawKey: DrawKey
        ): RiveSurfaceVulkan {
            if (!surface.surface.isValid) {
                throw RiveRenderException("Unable to create Android Surface")
            }

            var nativeSurface = 0L
            return try {
                nativeSurface = cppCreateSurface(
                    renderContext.nativeObjectPointer,
                    surface.surface,
                    surface.width,
                    surface.height
                )
                val riveSurface = RiveSurfaceVulkan(
                    nativeSurface,
                    surface,
                    commandQueue,
                    drawKey,
                    surface.width,
                    surface.height,
                    surface.resizable
                )
                nativeSurface = 0L
                riveSurface
            } catch (e: Throwable) {
                if (nativeSurface != 0L) {
                    commandQueue.deleteSurfaceNative(nativeSurface)
                }
                throw e
            }
        }
    }

    /**
     * Disposes native Vulkan surface resources, releases the Android surface source, and calls the
     * super class to dispose of its resources.
     *
     * Runs on the command server thread. See the note in [RiveSurface.close].
     */
    @WorkerThread
    override fun dispose() {
        super.dispose()
        closeableSurface.close()
    }
}
