package app.rive.core

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface
import android.view.TextureView
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
 * As it contains native resources, it implements [CheckableAutoCloseable] and should be
 * [closed][CheckableAutoCloseable.close] when no longer needed.
 */
abstract class RenderContext : CheckableAutoCloseable {
    /**
     * The native pointer to the backend-specific RenderContext object, held in a unique pointer.
     */
    protected abstract val cppPointer: UniquePointer

    /** The native pointer to the backend-specific RenderContext object. */
    val nativeObjectPointer: Long
        get() = cppPointer.pointer

    // Implemented by delegating to the unique pointer
    override fun close() = cppPointer.close()
    override val closed: Boolean
        get() = cppPointer.closed

    /**
     * Creates a backend-specific [RiveSurface].
     *
     * @param surfaceTexture The Android SurfaceTexture to render against, likely created from a
     *    [TextureView].
     * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
     * @param commandQueue The owning command queue, used to create render targets on the command
     *    server thread.
     * @return The created [RiveSurface].
     */
    abstract fun createSurface(
        surfaceTexture: SurfaceTexture,
        drawKey: DrawKey,
        commandQueue: CommandQueue
    ): RiveSurface

    /**
     * Creates an off-screen [RiveSurface] that renders into a pixel buffer instead of an Android
     * [Surface]. This surface can be used to capture rendered output for tasks such as snapshot
     * testing.
     *
     * @param width The width of the surface in pixels.
     * @param height The height of the surface in pixels.
     * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
     * @param commandQueue The owning command queue, used to create render targets on the command
     *    * server thread.
     *
     * @return The created [RiveSurface].
     */
    abstract fun createImageSurface(
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
 * As it contains native resources, it implements [CheckableAutoCloseable] and should be
 * [closed][CheckableAutoCloseable.close] when no longer needed.
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
     * Creates an [RiveEGLSurface] from the given Android [Surface].
     *
     * @param surfaceTexture The Android [SurfaceTexture] to render against, likely created from a
     *    [TextureView].
     * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
     * @param commandQueue The owning command queue, used to create render targets on the command
     *    server thread.
     * @return The created [RiveEGLSurface].
     * @throws RiveRenderException If unable to create the EGL surface or Rive render target.
     */
    override fun createSurface(
        surfaceTexture: SurfaceTexture,
        drawKey: DrawKey,
        commandQueue: CommandQueue
    ): RiveSurface {
        RiveLog.d(TAG) { "Creating Android Surface" }
        val surface = Surface(surfaceTexture)
        if (!surface.isValid) {
            throw RiveRenderException("Unable to create Android Surface from SurfaceTexture")
        }

        RiveLog.d(TAG) { "Creating EGL surface" }
        val eglSurface = EGL14.eglCreateWindowSurface(
            display,
            config,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            val error = EGLError.errorString(EGL14.eglGetError())
            RiveLog.e(TAG) { "eglCreateWindowSurface failed with error: $error" }
            throw RiveRenderException("Unable to create EGL surface", Throwable(error))
        }

        // The EGLSurface holds a reference to the underlying ANativeWindow, so we can release our
        // reference to it. The final reference is released when the EGLSurface is destroyed.
        surface.release()

        val dimensions = IntArray(2)
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_WIDTH, dimensions, 0)
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_HEIGHT, dimensions, 1)
        val width = dimensions[0]
        val height = dimensions[1]
        RiveLog.d(TAG) { "Created EGL surface ($width x $height)" }

        val renderTarget = commandQueue.createRiveRenderTarget(width, height)

        return RiveEGLSurface(
            surfaceTexture,
            eglSurface,
            display,
            renderTarget,
            drawKey,
            width,
            height
        )
    }

    /**
     * Creates an off-screen [RiveSurface] that renders into an EGL PBuffer instead of an Android
     * [SurfaceTexture]. This surface can be used to capture rendered output for tasks such as
     * snapshot testing.
     *
     * @param width The width of the surface in pixels.
     * @param height The height of the surface in pixels.
     * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
     * @param commandQueue The owning command queue, used to create render targets on the command
     *    server thread.
     * @return The created [RiveSurface].
     */
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

        val renderTarget = commandQueue.createRiveRenderTarget(width, height)

        return RiveEGLPBufferSurface(
            eglSurface,
            display,
            renderTarget,
            drawKey,
            width,
            height
        )
    }
}

/**
 * A backend agnostic collection of surface properties needed for rendering.
 * - A Rive render target, created natively which renders to the GL framebuffer
 * - A draw key, which uniquely identifies draw operations in the CommandQueue
 *
 * It also stores the width and height of the surface.
 *
 * This class assumes ownership of all resources and should be [closed][RiveSurface.close] when no
 * longer needed.
 *
 * Alone it is not sufficient for rendering, as it lacks a backend-specific surface, which is
 * provided by sub-classes.
 *
 * @param renderTargetPointer The native pointer to the Rive render target.
 * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
 * @param width The width of the surface in pixels.
 * @param height The height of the surface in pixels.
 */
abstract class RiveSurface(
    renderTargetPointer: Long,
    val drawKey: DrawKey,
    val width: Int,
    val height: Int
) : CheckableAutoCloseable {
    private external fun cppDeleteRenderTarget(pointer: Long)

    /**
     * Closes the render target unique pointer, which in turn disposes the RiveSurface at large.
     *
     * ⚠️ Do not call this directly from the main thread. It is meant to be called on the command
     * server thread as a scheduled close using [CommandQueue.destroyRiveSurface]. This ensures that
     * any draw calls in flight have a valid [TextureView] until completed.
     */
    @WorkerThread
    override fun close() = renderTargetPointer.close()
    override val closed: Boolean
        get() = renderTargetPointer.closed

    /**
     * Deletes the native render target.
     *
     * Sub-classes should override this method to dispose of any additional resources, calling
     * `super.dispose(pointer)` at the end.
     *
     * Called from the [render target's unique pointer][renderTargetPointer]. Runs on the command
     * server thread. See the note in [close].
     *
     * @param renderTargetPointer The native pointer to the Rive render target.
     */
    @CallSuper
    @WorkerThread
    protected open fun dispose(renderTargetPointer: Long) {
        RiveLog.d("Rive/RenderTarget") { "Deleting Rive render target" }
        cppDeleteRenderTarget(renderTargetPointer)
    }

    /** The native pointer to the Rive render target, held in a unique pointer. */
    val renderTargetPointer: UniquePointer =
        UniquePointer(renderTargetPointer, "Rive/RenderTarget", ::dispose)

    /** The native pointer to the backend-specific surface, e.g. EGLSurface for OpenGL. */
    abstract val surfaceNativePointer: Long
}

/**
 * A collection of four surface properties needed for rendering.
 * - An Android SurfaceTexture, provided by an Android SurfaceTextureListener
 * - An EGLSurface, created from a Surface which is is in turn created from the SurfaceTexture
 * - A Rive render target, created natively which renders to the GL framebuffer
 * - A draw key, which uniquely identifies draw operations in the CommandQueue
 *
 * It also stores the width and height of the surface.
 *
 * This class assumes ownership of all resources and should be [closed][RiveSurface.close] when no
 * longer needed.
 *
 * @param surfaceTexture The Android SurfaceTexture to render against, likely created from a
 *    [TextureView].
 * @param eglSurface The EGLSurface created from the Android Surface.
 * @param display The EGLDisplay used to create the EGLSurface, used for destroying it.
 * @param renderTargetPointer The native pointer to the Rive render target.
 * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
 * @param width The width of the surface in pixels.
 * @param height The height of the surface in pixels.
 */
class RiveEGLSurface(
    private val surfaceTexture: SurfaceTexture,
    private val eglSurface: EGLSurface,
    private val display: EGLDisplay,
    renderTargetPointer: Long,
    drawKey: DrawKey,
    width: Int,
    height: Int
) : RiveSurface(renderTargetPointer, drawKey, width, height), AutoCloseable {
    companion object {
        const val TAG = "Rive/EGLSurface"
    }

    /**
     * Destroys the EGLSurface, releases the SurfaceTexture, and calls the super class to dispose of
     * its resources.
     *
     * Runs on the command server thread. See the note in [close].
     *
     * @param renderTargetPointer The native pointer to the Rive render target. Passed to the base
     *    class implementation for deletion.
     * @throws RiveShutdownException If unable to destroy the EGL surface.
     */
    @WorkerThread
    override fun dispose(renderTargetPointer: Long) {
        // Destroy the EGL surface first...
        RiveLog.d(TAG) { "Destroying EGL surface" }
        val destroyed = EGL14.eglDestroySurface(display, eglSurface)
        if (!destroyed) {
            throw RiveShutdownException("Unable to destroy EGL surface")
        }

        /**
         * This originally came from [TextureView.SurfaceTextureListener.onSurfaceTextureAvailable].
         * In [TextureView.SurfaceTextureListener.onSurfaceTextureDestroyed], we return `false`,
         * which means we are responsible for releasing the texture. We do this to ensure the
         * texture lives long enough to complete any active draws, avoiding teardown races.
         */
        RiveLog.d(TAG) { "Releasing SurfaceTexture" }
        surfaceTexture.release()

        // ... Then dispose of base class resources
        super.dispose(renderTargetPointer)
    }

    /** The native pointer to the EGLSurface. */
    override val surfaceNativePointer: Long
        get() = eglSurface.nativeHandle
}

/** A PBuffer-backed EGL surface used for off-screen rendering and image capture. */
class RiveEGLPBufferSurface(
    private val eglSurface: EGLSurface,
    private val display: EGLDisplay,
    renderTargetPointer: Long,
    drawKey: DrawKey,
    width: Int,
    height: Int
) : RiveSurface(renderTargetPointer, drawKey, width, height), AutoCloseable {
    companion object {
        const val TAG = "Rive/EGLPBufferSurface"
    }

    /**
     * Destroys the EGLSurface and calls the super class to dispose of other resources.
     *
     * Runs on the command server thread. See the note in [close].
     *
     * @param renderTargetPointer The native pointer to the Rive render target. Passed to the base
     *    class implementation for deletion.
     * @throws RiveShutdownException If unable to destroy the EGL surface.
     */
    @WorkerThread
    override fun dispose(renderTargetPointer: Long) {
        // Destroy the EGL PBuffer surface first...
        RiveLog.d(TAG) { "Destroying EGL PBuffer surface" }
        val destroyed = EGL14.eglDestroySurface(display, eglSurface)
        if (!destroyed) {
            throw RiveShutdownException("Unable to destroy EGL PBuffer surface")
        }

        // ... Then dispose of other resources
        super.dispose(renderTargetPointer)
    }

    override val surfaceNativePointer: Long
        get() = eglSurface.nativeHandle
}
