package app.rive.core

import android.opengl.EGL14
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface
import androidx.annotation.CallSuper
import androidx.annotation.WorkerThread
import app.rive.RiveLog
import app.rive.RiveRenderException
import app.rive.RiveShutdownException

/**
 * A backend agnostic collection of surface properties needed for rendering.
 * - A draw key, which uniquely identifies draw operations in the CommandQueue
 * - The dimensions of the surface in pixels
 *
 * ⚠️ This class assumes ownership of all resources and should be [closed][RiveSurface.close] when
 * no longer needed. Closing schedules ordered disposal on the owning [CommandQueue].
 *
 * Alone it is not sufficient for rendering, as it lacks a backend-specific surface, which is
 * provided by subclasses.
 *
 * @param owningCommandQueue Command queue that owns this surface and performs ordered disposal. The
 *    surface acquires its own reference so the queue stays alive until surface disposal has run.
 * @param surfaceNativePointer Opaque native backend surface resource owned by this surface.
 * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
 * @param width The width of the surface in pixels.
 * @param height The height of the surface in pixels.
 * @param resizable Whether this surface supports in-place size changes via [resize].
 */
abstract class RiveSurface internal constructor(
    owningCommandQueue: CommandQueue,
    surfaceNativePointer: Long,
    val drawKey: DrawKey,
    width: Int,
    height: Int,
    /**
     * Whether this surface supports in-place size changes.
     *
     * For surfaces created from a [CloseableSurface], this value is copied from the backing
     * creation object at construction time. Window surfaces backed by resizable Android resources,
     * such as [SurfaceTextureSurface], can resize in place. Fixed-size surfaces, such as
     * [ImageReaderSurface] and off-screen image surfaces, must be recreated at the desired size
     * instead.
     */
    val resizable: Boolean
) : CheckableAutoCloseable {
    companion object {
        /**
         * Deletes a native surface resource.
         *
         * Normal surface disposal calls this on the command server thread through [dispose].
         * Creation-failure cleanup may call it from the creating thread before the surface has ever
         * been used for rendering.
         */
        internal fun cppDeleteSurface(pointer: Long) = cppDeleteSurfaceNative(pointer)

        @JvmStatic
        private external fun cppDeleteSurfaceNative(pointer: Long)

        @JvmStatic
        @WorkerThread
        private external fun cppResizeSurface(
            surfacePointer: Long,
            width: Int,
            height: Int
        )
    }

    /** The width of the surface in pixels. */
    var width: Int = width
        private set

    /** The height of the surface in pixels. */
    var height: Int = height
        private set

    private val commandQueue: CommandQueue = owningCommandQueue.also {
        // Hold a reference to the command queue, ensuring we can schedule work on it for disposal.
        // The matching release is in the closer.
        it.acquire("RiveSurface")
    }

    private val closer = CloseOnce("RiveSurface") {
        commandQueue.cancelDraw(drawKey)
        commandQueue.runOnCommandServer {
            dispose()
        }
        // Matching release to acquire in the constructor. If this is the final reference, release
        // queues command server shutdown after the disposal work above.
        commandQueue.release("RiveSurface", "Surface closed")
    }

    /**
     * Schedules surface disposal on the owning command queue.
     *
     * This is safe to call from the main thread. After calling [close], callers must not use this
     * surface again. Pending coalesced draws for this surface are canceled before native disposal
     * is queued. Draws already admitted to execution may still complete.
     */
    override fun close() = closer.close()
    override val closed: Boolean
        get() = closer.closed

    /**
     * Disposes native surface resources.
     *
     * Subclasses should override this method to dispose of any additional resources, calling
     * `super.dispose()` at the end.
     *
     * Runs on the command server thread. See the note in [close].
     */
    @CallSuper
    @WorkerThread
    protected open fun dispose() = surfaceNativePointer.close()

    /**
     * Resizes this surface without replacing the Android or backend window surface.
     *
     * Cancels pending coalesced draws before queueing backend render-target invalidation, so a
     * stale draw cannot run after the resize command but before the next draw phase. Render-target
     * invalidation is queued because concrete backend targets are created and used on the command
     * server thread; resizing them from the caller thread would race with draw execution and
     * backend context ownership.
     *
     * @param width New surface width in physical pixels.
     * @param height New surface height in physical pixels.
     * @throws IllegalArgumentException If [width] or [height] is not positive.
     * @throws IllegalStateException If this surface is already closed or does not support resizing.
     */
    internal fun resize(width: Int, height: Int) {
        require(width > 0 && height > 0) {
            "RiveSurface resize requires a positive width and height."
        }
        check(!closed) { "Cannot resize a closed RiveSurface" }
        check(resizable) { "Cannot resize a fixed-size RiveSurface" }
        if (this.width == width && this.height == height) {
            return
        }

        commandQueue.cancelDraw(drawKey)
        this.width = width
        this.height = height
        commandQueue.runOnCommandServer {
            if (!closed) {
                resizeNativeResources(width, height)
            }
        }
    }

    /**
     * Invalidates native resources that are sized from this surface.
     *
     * Runs on the command server thread so render-target disposal and backend-specific surface
     * invalidation remain ordered with draw work.
     */
    @WorkerThread
    protected open fun resizeNativeResources(width: Int, height: Int) {
        cppResizeSurface(
            surfaceNativePointer.pointer,
            width,
            height
        )
    }

    /**
     * Opaque native backend surface resource.
     *
     * The concrete backend surface owns its render target and is disposed through command queue
     * ordering when [close] is called.
     */
    val surfaceNativePointer: UniquePointer = UniquePointer(
        surfaceNativePointer,
        "Rive/Surface"
    ) { pointer ->
        RiveLog.d("Rive/Surface") { "Deleting Rive native surface" }
        cppDeleteSurface(pointer)
    }
}

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
                    cppDeleteSurface(nativeSurface)
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
            renderContext: RenderContextVulkan,
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
                    cppDeleteSurface(nativeSurface)
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
