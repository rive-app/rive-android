package app.rive.core

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface
import androidx.annotation.CallSuper
import androidx.annotation.WorkerThread
import app.rive.RiveInitializationException
import app.rive.RiveLog
import app.rive.RiveRenderException
import app.rive.RiveShutdownException

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
abstract class RenderContext : CheckableAutoCloseable {
    /**
     * The native pointer to the backend-specific RenderContext object, held in a unique pointer.
     */
    protected abstract val cppPointer: UniquePointer

    /** The native pointer to the backend-specific RenderContext object. */
    val nativeObjectPointer: Long
        get() = cppPointer.pointer

    // Implemented by delegating to the unique pointer.
    override fun close() = cppPointer.close()
    override val closed: Boolean
        get() = cppPointer.closed

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
    @Throws(RiveRenderException::class, IllegalStateException::class)
    internal abstract fun createSurface(
        surface: CloseableSurface,
        drawKey: DrawKey,
        commandQueue: CommandQueue
    ): RiveSurface

    /**
     * Creates an off-screen [RiveSurface] that renders into a pixel buffer instead of an Android
     * [Surface]. This surface can be used to capture rendered output for tasks such as snapshot
     * testing.
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
    @Throws(
        IllegalArgumentException::class,
        RiveRenderException::class,
        IllegalStateException::class
    )
    internal abstract fun createImageSurface(
        width: Int,
        height: Int,
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
) : RenderContext(), CheckableAutoCloseable {
    private external fun cppConstructor(display: Long, context: Long): Long
    private external fun cppDelete(pointer: Long)

    /**
     * Creates an opaque native render target handle for a [RiveSurface].
     *
     * The handle is safe to pass to later draw commands immediately. Backend-specific GL resources
     * are created lazily on the command server thread when the surface is first used for rendering.
     */
    private external fun cppCreateRiveRenderTarget(width: Int, height: Int): Long
    private external fun cppDeleteRiveRenderTarget(pointer: Long)

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

    /** The native pointer to the RenderContextGL object, held in a unique pointer. */
    override val cppPointer =
        UniquePointer(
            cppConstructor(display.nativeHandle, context.nativeHandle),
            TAG,
            ::dispose
        )

    /**
     * Disposes of the EGL context and display, and deletes the native RenderContextGL object.
     *
     * @throws RiveShutdownException If unable to destroy the EGL context or terminate the EGL
     *    display.
     */
    private fun dispose(address: Long) {
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

        RiveLog.d(TAG) { "Deleting RenderContextGL native object" }
        cppDelete(address)
    }

    /**
     * Creates a [RiveEGLSurface] from the given Android [CloseableSurface].
     *
     * ⚠️ The returned [RiveSurface] must be [closed][RiveSurface.close] when no longer needed.
     *
     * @param surface Owned Android surface source to render against.
     * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
     * @param commandQueue The owning command queue. The created surface acquires a reference so it
     *    can later schedule ordered disposal.
     * @return The created [RiveEGLSurface].
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

        var renderTarget = 0L
        return try {
            val dimensions = IntArray(2)
            EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_WIDTH, dimensions, 0)
            EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_HEIGHT, dimensions, 1)
            val width = dimensions[0]
            val height = dimensions[1]
            RiveLog.d(TAG) { "Created EGL surface ($width x $height)" }

            renderTarget = cppCreateRiveRenderTarget(width, height)

            val riveSurface = RiveEGLSurface(
                eglSurface,
                display,
                surface,
                commandQueue,
                renderTarget,
                drawKey,
                width,
                height
            )
            renderTarget = 0L
            riveSurface
        } catch (e: Throwable) {
            // Do not leak the render target or EGL surface if we failed to create the
            // RiveEGLSurface wrapper.
            if (renderTarget != 0L) {
                cppDeleteRiveRenderTarget(renderTarget)
            }
            EGL14.eglDestroySurface(display, eglSurface)
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

        var renderTarget = 0L
        return try {
            renderTarget = cppCreateRiveRenderTarget(width, height)
            val riveSurface = RiveEGLPBufferSurface(
                eglSurface,
                display,
                commandQueue,
                renderTarget,
                drawKey,
                width,
                height
            )
            renderTarget = 0L
            riveSurface
        } catch (e: Throwable) {
            // Do not leak the render target or EGL surface if we failed to create the
            // RiveEGLPBufferSurface wrapper.
            if (renderTarget != 0L) {
                cppDeleteRiveRenderTarget(renderTarget)
            }
            EGL14.eglDestroySurface(display, eglSurface)
            throw e
        }
    }
}

/**
 * A backend agnostic collection of surface properties needed for rendering.
 * - An opaque native Rive render target handle
 * - A draw key, which uniquely identifies draw operations in the CommandQueue
 *
 * It also stores the width and height of the surface.
 *
 * ⚠️ This class assumes ownership of all resources and should be [closed][RiveSurface.close] when
 * no longer needed. Closing schedules ordered disposal on the owning [CommandQueue].
 *
 * Alone it is not sufficient for rendering, as it lacks a backend-specific surface, which is
 * provided by subclasses.
 *
 * @param owningCommandQueue Command queue that owns this surface and performs ordered disposal. The
 *    surface acquires its own reference so the queue stays alive until surface disposal has run.
 * @param renderTargetPointer Opaque native render target handle owned by this surface.
 * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
 * @param width The width of the surface in pixels.
 * @param height The height of the surface in pixels.
 */
abstract class RiveSurface(
    owningCommandQueue: CommandQueue,
    renderTargetPointer: Long,
    val drawKey: DrawKey,
    val width: Int,
    val height: Int
) : CheckableAutoCloseable {
    private external fun cppDeleteRenderTarget(pointer: Long)

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
    protected open fun dispose() = renderTargetPointer.close()

    /**
     * Opaque native render target handle.
     *
     * The concrete backend target is created lazily on the command server thread. This handle is
     * owned by the surface and disposed through command queue ordering when [close] is called.
     */
    val renderTargetPointer: UniquePointer = UniquePointer(
        renderTargetPointer,
        "Rive/RenderTarget"
    ) { pointer ->
        RiveLog.d("Rive/RenderTarget") { "Deleting Rive render target" }
        cppDeleteRenderTarget(pointer)
    }

    /** The native pointer to the backend-specific surface, e.g. EGLSurface for OpenGL. */
    abstract val surfaceNativePointer: Long
}

/**
 * A collection of three surface properties needed for rendering.
 * - An EGLSurface, created from an Android Surface
 * - An opaque native Rive render target handle
 * - A draw key, which uniquely identifies draw operations in the CommandQueue
 *
 * Meant for use with and created from a [RenderContextGL].
 *
 * It also stores the width and height of the surface.
 *
 * ⚠️ This class assumes ownership of all resources and should be [closed][RiveSurface.close] when
 * no longer needed.
 *
 * @param eglSurface The EGLSurface created from the Android Surface.
 * @param display The EGLDisplay used to create the EGLSurface, used for destroying it.
 * @param closeableSurface Android surface source closed after the EGL surface is destroyed.
 * @param commandQueue Command queue that owns this surface and performs ordered disposal.
 * @param renderTargetPointer Opaque native render target handle owned by this surface.
 * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
 * @param width The width of the surface in pixels.
 * @param height The height of the surface in pixels.
 */
class RiveEGLSurface(
    private val eglSurface: EGLSurface,
    private val display: EGLDisplay,
    private val closeableSurface: CloseableSurface,
    commandQueue: CommandQueue,
    renderTargetPointer: Long,
    drawKey: DrawKey,
    width: Int,
    height: Int
) : RiveSurface(commandQueue, renderTargetPointer, drawKey, width, height), AutoCloseable {
    companion object {
        const val TAG = "Rive/EGLSurface"
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

    /** The native pointer to the EGLSurface. */
    override val surfaceNativePointer: Long
        get() = eglSurface.nativeHandle
}

/**
 * A PBuffer-backed EGL surface used for off-screen rendering and image capture.
 *
 * Meant for use with and created from a [RenderContextGL].
 *
 * ⚠️ This class assumes ownership of all resources and should be [closed][RiveSurface.close] when
 * no longer needed.
 */
class RiveEGLPBufferSurface(
    private val eglSurface: EGLSurface,
    private val display: EGLDisplay,
    commandQueue: CommandQueue,
    renderTargetPointer: Long,
    drawKey: DrawKey,
    width: Int,
    height: Int
) : RiveSurface(commandQueue, renderTargetPointer, drawKey, width, height), AutoCloseable {
    companion object {
        const val TAG = "Rive/EGLPBufferSurface"
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

    override val surfaceNativePointer: Long
        get() = eglSurface.nativeHandle
}
