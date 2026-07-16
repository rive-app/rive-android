package app.rive.core

import androidx.annotation.WorkerThread
import app.rive.RiveRenderException

/**
 * Vulkan-backed off-screen image surface.
 *
 * @param nativeSurfacePointer Native Vulkan image surface wrapper pointer.
 * @param commandQueue Command queue that owns ordered disposal.
 * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
 * @param width The width of the surface in pixels.
 * @param height The height of the surface in pixels.
 */
internal class RiveSurfaceVulkanImage(
    nativeSurfacePointer: Long,
    commandQueue: CommandQueue,
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
        const val TAG = "Rive/SurfaceVulkanImage"

        @JvmStatic
        private external fun cppCreateImageSurface(
            renderContextPointer: Long,
            width: Int,
            height: Int
        ): Long

        /**
         * Creates a Vulkan-backed off-screen [RiveSurface].
         *
         * The returned surface owns the native Vulkan image surface wrapper and releases it through
         * ordered command queue disposal when the returned [RiveSurfaceVulkanImage] is closed.
         *
         * @param renderContext Vulkan render context used to create the native image surface.
         * @param width The width of the surface in pixels.
         * @param height The height of the surface in pixels.
         * @param commandQueue Command queue that owns ordered disposal.
         * @param drawKey The key used to uniquely identify draw operations in the CommandQueue.
         * @return The created [RiveSurfaceVulkanImage].
         * @throws IllegalArgumentException If [width] or [height] is not positive.
         * @throws RiveRenderException If native image surface creation fails.
         * @throws IllegalStateException If the surface cannot acquire the command queue.
         */
        @Throws(
            IllegalArgumentException::class,
            RiveRenderException::class,
            IllegalStateException::class
        )
        internal fun create(
            renderContext: RenderContext,
            width: Int,
            height: Int,
            commandQueue: CommandQueue,
            drawKey: DrawKey
        ): RiveSurfaceVulkanImage {
            require(width > 0 && height > 0) {
                "Image surfaces require a positive width and height."
            }

            var nativeSurface = 0L
            return try {
                nativeSurface = cppCreateImageSurface(
                    renderContext.nativeObjectPointer,
                    width,
                    height
                )
                val riveSurface = RiveSurfaceVulkanImage(
                    nativeSurface,
                    commandQueue,
                    drawKey,
                    width,
                    height
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
     * Disposes native Vulkan image surface resources and calls the super class to dispose of its
     * resources.
     *
     * Runs on the command server thread. See the note in [RiveSurface.close].
     */
    @WorkerThread
    override fun dispose() {
        super.dispose()
    }
}
