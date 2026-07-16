package app.rive.core

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import app.rive.RenderBackend
import app.rive.RiveInitializationException
import app.rive.RiveLog
import app.rive.RiveRenderException
import app.rive.RiveShutdownException

internal actual fun createPlatformBridge(): CommandQueueBridge {
    RiveNative.ensureLoaded()
    return CommandQueueJNIBridge()
}

internal actual fun createPlatformRenderContext(renderBackend: RenderBackend): RenderContext {
    RiveNative.ensureLoaded()
    // layoutlib previews render through the desktop library, which is Vulkan-only
    // (EGL does not exist on the host JVM).
    if (RiveNative.isHostJvm) return RenderContextVulkan()
    return when (renderBackend) {
        RenderBackend.Vulkan -> RenderContextVulkan()
        RenderBackend.OpenGL -> RenderContextGL()
    }
}

/**
 * A [RenderContext] that can additionally create window surfaces from Android
 * [CloseableSurface]s.
 */
internal abstract class AndroidRenderContext : RenderContext() {
    /**
     * Creates a backend-specific [RiveSurface] from an Android [CloseableSurface].
     *
     * ⚠️ The returned [RiveSurface] must be [closed][RiveSurface.close] when no longer needed.
     *
     * @param surface Owned Android surface source to render against.
     * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
     * @param commandQueue The owning command queue. The created surface acquires a reference so it
     *    can later schedule ordered disposal.
     * @return The created [RiveSurface].
     * @throws RiveRenderException If the backend cannot create a renderable surface.
     * @throws IllegalStateException If the surface cannot acquire the command queue.
     */
    internal abstract fun createSurface(
        surface: CloseableSurface,
        drawKey: DrawKey,
        commandQueue: CommandQueue
    ): RiveSurface
}


/**
 * An OpenGL ES rendering context implementation of [RenderContext].
 *
 * It creates and manages an EGL display, config, and context for rendering with OpenGL ES 2.0.
 *
 * ⚠️ As it contains native resources, it implements [CheckableAutoCloseable] and should be
 * [closed][CheckableAutoCloseable.close] when no longer needed, unless it is passed to a
 * [CommandQueue], in which case it assumes ownership of this object and will close it when it is
 * disposed.
 *
 * @param display The EGL display.
 * @param config The EGL config.
 * @param context The EGL context.
 * @throws RiveInitializationException If unable to create or initialize any EGL resources.
 */
internal data class RenderContextGL(
    val display: EGLDisplay = createDisplay(),
    val config: EGLConfig = createConfig(display),
    val context: EGLContext = createContext(display, config)
) : AndroidRenderContext() {
    private external fun cppConstructor(display: Long, context: Long): Long
    private external fun cppDelete(pointer: Long)

    /** Creates a native GL surface wrapper for an EGL surface. */
    private external fun cppCreateSurface(eglSurface: Long, width: Int, height: Int): Long

    companion object {
        const val TAG = "Rive/RenderContextGL"

        /**
         * Gets and initializes the EGL display.
         *
         * @throws RiveInitializationException If unable to get or initialize the EGL display.
         */
        private fun createDisplay(): EGLDisplay {
            RiveLog.d(TAG) { "Getting EGL display" }
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                val error = EGLError.errorString(EGL14.eglGetError())
                RiveLog.e(TAG) { "eglGetDisplay failed with error: $error" }
                throw RiveInitializationException("Unable to get EGL display", Throwable(error))
            }

            RiveLog.d(TAG) { "Initializing EGL" }
            val majorVersion = IntArray(1)
            val minorVersion = IntArray(1)
            if (!EGL14.eglInitialize(display, majorVersion, 0, minorVersion, 0)) {
                val error = EGLError.errorString(EGL14.eglGetError())
                RiveLog.e(TAG) { "eglInitialize failed with error: $error" }
                throw RiveInitializationException("Unable to initialize EGL", Throwable(error))
            }
            RiveLog.d(TAG) { "EGL initialized with version ${majorVersion[0]}.${minorVersion[0]}" }

            return display
        }

        /**
         * Chooses an EGL config for OpenGL ES 2.0 windowed rendering with 8 bits per channel RGBA
         * and 8 bits for the stencil buffer.
         *
         * @param display The EGL display.
         * @throws RiveInitializationException If unable to find a suitable EGL config.
         */
        private fun createConfig(display: EGLDisplay): EGLConfig {
            val configAttributes = intArrayOf(
                // We want OpenGL ES 2.0
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                // Request both window and pbuffer surfaces
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
                // 8 bits per channel RGBA
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                // No depth buffer
                EGL14.EGL_DEPTH_SIZE, 0,
                // 8 bits for the stencil buffer
                EGL14.EGL_STENCIL_SIZE, 8,
                EGL14.EGL_NONE
            )

            RiveLog.d(TAG) { "Choosing EGL config" }
            val numConfigs = IntArray(1)
            val configs = arrayOfNulls<EGLConfig>(1)
            val success = EGL14.eglChooseConfig(
                display,
                configAttributes,
                0,
                configs,
                0,
                configs.size,
                numConfigs,
                0
            )
            if (!success) {
                val error = EGLError.errorString(EGL14.eglGetError())
                RiveLog.e(TAG) { "eglChooseConfig failed with error: $error" }
                throw RiveInitializationException(
                    "EGL config creation failed: $error",
                    Throwable(error)
                )
            } else if (numConfigs[0] <= 0 || configs[0] == null) {
                RiveLog.e(TAG) { "eglChooseConfig could not find a suitable config" }
                throw RiveInitializationException("Unable to find a suitable EGL config")
            } else {
                val chosenConfig = configs[0]!!
                fun attr(name: Int): Int {
                    val value = IntArray(1)
                    EGL14.eglGetConfigAttrib(display, chosenConfig, name, value, 0)
                    return value[0]
                }

                RiveLog.d(TAG) {
                    "EGL config chosen successfully:\n" +
                            "  R=${attr(EGL14.EGL_RED_SIZE)}\n" +
                            "  G=${attr(EGL14.EGL_GREEN_SIZE)}\n" +
                            "  B=${attr(EGL14.EGL_BLUE_SIZE)}\n" +
                            "  A=${attr(EGL14.EGL_ALPHA_SIZE)}\n" +
                            "  Depth=${attr(EGL14.EGL_DEPTH_SIZE)}\n" +
                            "  Stencil=${attr(EGL14.EGL_STENCIL_SIZE)}"
                }

                return chosenConfig
            }
        }

        /**
         * Creates an EGL context for OpenGL ES 2.0 rendering.
         *
         * @param display The EGL display.
         * @param config The EGL config.
         * @throws RiveInitializationException If unable to create the EGL context.
         */
        private fun createContext(display: EGLDisplay, config: EGLConfig): EGLContext {
            val contextAttributes = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            RiveLog.d(TAG) { "Creating EGL context" }
            val context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                contextAttributes,
                0
            )
            if (context == EGL14.EGL_NO_CONTEXT) {
                val error = EGLError.errorString(EGL14.eglGetError())
                RiveLog.e(TAG) { "eglCreateContext failed with error: $error" }
                throw RiveInitializationException("Unable to create EGL context", Throwable(error))
            }

            return context
        }
    }

    private val cppPointer = UniquePointer(
        cppConstructor(display.nativeHandle, context.nativeHandle),
        TAG
    ) { pointer ->
        RiveLog.d(TAG) { "Deleting RenderContextGL native object" }
        cppDelete(pointer)
    }

    /** The native pointer to the RenderContextGL object. */
    override val nativeObjectPointer: Long
        get() = cppPointer.pointer

    /**
     * Disposes of the EGL context and display, and deletes the native RenderContextGL object.
     *
     * @throws RiveShutdownException If unable to destroy the EGL context or terminate the EGL
     *    display.
     */
    override fun dispose() {
        RiveLog.d(TAG) { "Destroying EGL context" }
        val destroyed = EGL14.eglDestroyContext(display, context)
        if (!destroyed) {
            val error = EGLError.errorString(EGL14.eglGetError())
            RiveLog.e(TAG) { "eglDestroyContext failed with error: $error" }
            throw RiveShutdownException("Unable to destroy EGL context", Throwable(error))
        }

        RiveLog.d(TAG) { "Terminating EGL display" }
        val terminated = EGL14.eglTerminate(display)
        if (!terminated) {
            val error = EGLError.errorString(EGL14.eglGetError())
            RiveLog.e(TAG) { "eglTerminate failed with error: $error" }
            throw RiveShutdownException("Unable to terminate EGL display", Throwable(error))
        }

        cppPointer.close()
    }

    /**
     * Creates a [RiveSurfaceGL] from the given Android [CloseableSurface].
     *
     * ⚠️ The returned [RiveSurface] must be [closed][RiveSurface.close] when no longer needed.
     *
     * @param surface Owned Android surface source to render against.
     * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
     * @param commandQueue The owning command queue. The created surface acquires a reference so it
     *    can later schedule ordered disposal.
     * @return The created [RiveSurfaceGL].
     * @throws RiveRenderException If the backing Android surface is invalid or EGL window surface
     *    creation fails.
     * @throws IllegalStateException If the surface cannot acquire the command queue.
     */
    @Throws(RiveRenderException::class, IllegalStateException::class)
    override fun createSurface(
        surface: CloseableSurface,
        drawKey: DrawKey,
        commandQueue: CommandQueue
    ): RiveSurface {
        if (!surface.surface.isValid) {
            throw RiveRenderException("Unable to create Android Surface")
        }

        RiveLog.d(TAG) { "Creating EGL window surface" }
        val eglSurface = EGL14.eglCreateWindowSurface(
            display,
            config,
            surface.surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            val error = EGLError.errorString(EGL14.eglGetError())
            RiveLog.e(TAG) { "eglCreateWindowSurface failed with error: $error" }
            throw RiveRenderException("Unable to create EGL surface", Throwable(error))
        }

        var nativeSurface = 0L
        return try {
            val width = surface.width
            val height = surface.height
            RiveLog.d(TAG) { "Created EGL surface ($width x $height)" }

            nativeSurface = cppCreateSurface(eglSurface.nativeHandle, width, height)

            val riveSurface = RiveSurfaceGL(
                eglSurface,
                display,
                surface,
                commandQueue,
                nativeSurface,
                drawKey,
                width,
                height,
                surface.resizable
            )
            nativeSurface = 0L
            riveSurface
        } catch (e: Throwable) {
            // Do not leak the EGL surface if we failed to create the RiveSurfaceGL wrapper.
            EGL14.eglDestroySurface(display, eglSurface)
            if (nativeSurface != 0L) {
                commandQueue.deleteSurfaceNative(nativeSurface)
            }
            throw e
        }
    }

    /**
     * Creates an off-screen [RiveSurface] that renders into an EGL PBuffer. This surface can be
     * used to capture rendered output for tasks such as snapshot testing.
     *
     * ⚠️ The returned [RiveSurface] must be [closed][RiveSurface.close] when no longer needed.
     *
     * @param width The width of the surface in pixels.
     * @param height The height of the surface in pixels.
     * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
     * @param commandQueue The owning command queue. The created surface acquires a reference so it
     *    can later schedule ordered disposal.
     * @return The created [RiveSurface].
     * @throws IllegalArgumentException If [width] or [height] is not positive.
     * @throws RiveRenderException If EGL PBuffer surface creation fails.
     * @throws IllegalStateException If the surface cannot acquire the command queue.
     */
    @Throws(
        IllegalArgumentException::class,
        RiveRenderException::class,
        IllegalStateException::class
    )
    override fun createImageSurface(
        width: Int,
        height: Int,
        drawKey: DrawKey,
        commandQueue: CommandQueue,
    ): RiveSurface {
        require(width > 0 && height > 0) { "Image surfaces require a positive width and height." }
        RiveLog.d(TAG) { "Creating EGL PBuffer surface ($width x $height)" }
        val attrs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        val eglSurface = EGL14.eglCreatePbufferSurface(display, config, attrs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            val error = EGLError.errorString(EGL14.eglGetError())
            RiveLog.e(TAG) { "eglCreatePbufferSurface failed with error: $error" }
            throw RiveRenderException("Unable to create EGL PBuffer surface", Throwable(error))
        }

        var nativeSurface = 0L
        return try {
            nativeSurface = cppCreateSurface(eglSurface.nativeHandle, width, height)
            val riveSurface = RiveSurfaceGLPBuffer(
                eglSurface,
                display,
                commandQueue,
                nativeSurface,
                drawKey,
                width,
                height
            )
            nativeSurface = 0L
            riveSurface
        } catch (e: Throwable) {
            // Do not leak the EGL surface if we failed to create the RiveSurfaceGLPBuffer wrapper.
            EGL14.eglDestroySurface(display, eglSurface)
            if (nativeSurface != 0L) {
                commandQueue.deleteSurfaceNative(nativeSurface)
            }
            throw e
        }
    }
}

/**
 * Vulkan rendering context implementation of [RenderContext].
 *
 * Native code owns the Vulkan instance, device, Android surface, swapchain, and Rive Vulkan
 * context. Kotlin owns only the Android [Surface] wrapper lifetime and opaque native handles.
 *
 * @throws RiveInitializationException If native Vulkan resources cannot be initialized.
 */
internal class RenderContextVulkan : AndroidRenderContext() {
    private external fun cppConstructor(): Long
    private external fun cppDelete(pointer: Long)

    companion object {
        const val TAG = "Rive/RenderContextVulkan"
    }

    private val cppPointer = UniquePointer(cppConstructor(), TAG) { pointer ->
        RiveLog.d(TAG) { "Deleting RenderContextVulkan native object" }
        cppDelete(pointer)
    }

    /** The native pointer to the RenderContextVulkan object. */
    override val nativeObjectPointer: Long
        get() = cppPointer.pointer

    override fun dispose() = cppPointer.close()

    @Throws(RiveRenderException::class, IllegalStateException::class)
    override fun createSurface(
        surface: CloseableSurface,
        drawKey: DrawKey,
        commandQueue: CommandQueue
    ): RiveSurface = RiveSurfaceVulkan.create(this, surface, commandQueue, drawKey)

    @Throws(
        IllegalArgumentException::class,
        RiveRenderException::class,
        IllegalStateException::class
    )
    override fun createImageSurface(
        width: Int,
        height: Int,
        drawKey: DrawKey,
        commandQueue: CommandQueue,
    ): RiveSurface = RiveSurfaceVulkanImage.create(this, width, height, commandQueue, drawKey)

}
