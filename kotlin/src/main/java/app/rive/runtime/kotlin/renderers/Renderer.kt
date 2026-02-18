package app.rive.runtime.kotlin.renderers

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import app.rive.RiveLog
import app.rive.runtime.kotlin.SharedSurface
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.NativeObject
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive

abstract class Renderer(
    @get:VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var type: RendererType = Rive.defaultRendererType,
    val trace: Boolean = false,
) : NativeObject(NULL_POINTER),
    Choreographer.FrameCallback {
    companion object {
        private const val TAG = "RiveL/Renderer"
    }

    // From NativeObject
    external override fun cppDelete(pointer: Long)

    private external fun cppStart(rendererPointer: Long)
    private external fun cppStop(rendererPointer: Long)
    private external fun cppSave(rendererPointer: Long)
    private external fun cppRestore(rendererPointer: Long)
    private external fun cppWidth(rendererPointer: Long): Int
    private external fun cppHeight(rendererPointer: Long): Int
    private external fun cppAvgFps(rendererPointer: Long): Float
    private external fun cppDoFrame(rendererPointer: Long)
    private external fun cppSetSurface(surface: Surface, rendererPointer: Long)
    private external fun cppDestroySurface(rendererPointer: Long)
    private external fun cppAlign(
        cppPointer: Long,
        fit: Fit,
        alignment: Alignment,
        targetBounds: RectF,
        srcBounds: RectF,
        scaleFactor: Float,
    )

    private external fun cppTransform(
        cppPointer: Long,
        x: Float,
        sy: Float,
        sx: Float,
        y: Float,
        tx: Float,
        ty: Float,
    )

    /** Instantiates JNIRenderer in C++ */
    private external fun constructor(trace: Boolean, type: Int): Long

    @CallSuper
    open fun make() {
        if (!hasCppObject) {
            cppPointer = constructor(trace, type.value)
            refs.incrementAndGet()
        }
    }

    /**
     * Helper function to reassign the renderer type. This might be necessary if [constructor]
     * couldn't build the renderer with [type] but had to fall back to a different value
     * (e.g. the Rive Renderer isn't available on emulators and it defaults back to Canvas).
     */
    @JvmName("setRendererType")
    internal fun setRendererType(newType: Int) {
        if (newType != type.value) {
            type = RendererType.fromIndex(newType)
        }
    }

    var isPlaying: Boolean = false
        private set
    var isAttached: Boolean = false

    private var sharedSurface: SharedSurface? = null

    /**
     * A lock to synchronize access to the C++ renderer object between the UI thread (which handles
     * lifecycle events like `delete()`) and the Choreographer thread (which executes `doFrame()`).
     * This prevents a race condition where the UI thread might nullify the C++ pointer while the
     * worker thread is still using it.
     */
    val frameLock = Any()


    @WorkerThread
    abstract fun draw()

    @WorkerThread
    abstract fun advance(elapsed: Float)

    /**
     * Starts the renderer and registers for frameCallbacks.
     *
     * Goal: When we trigger [start], [doFrame] gets called once per frame until we stop or the
     * animation finishes.
     *
     * Gotchas:
     * - [scheduleFrame] triggers callbacks to [doFrame] which in turn schedules more frames
     * - If we call [scheduleFrame] multiple times we enter multiple parallel animations loops
     * - To avoid this we check [isPlaying] and deregister
     *   [FrameCallbacks][Choreographer.FrameCallback] when stop is called by users
     */
    fun start() {
        if (isPlaying) return
        if (!isAttached) return
        if (!hasCppObject) return
        isPlaying = true
        cppStart(cppPointer)
        // Register for a new frame.
        scheduleFrame()
    }

    /**
     * Sets the drawing surface for the renderer.
     *
     * @deprecated This method is dangerous as it does not correctly manage the Surface's lifecycle,
     *    which can lead to application crashes. Its internal implementation has been patched to be
     *    safer, but it will be removed in a future major version. Prefer using higher-level APIs
     *    like `setRiveResource`.
     */
    @Deprecated(
        message = "This low-level method can cause crashes and will be removed. Prefer using higher-level APIs.",
        level = DeprecationLevel.WARNING
    )
    fun setSurface(surface: Surface) {
        setSurface(SharedSurface(surface))
    }

    /**
     * Sets the drawing surface for the renderer.
     *
     * This method is thread-safe. It acquires a reference to the [SharedSurface], ensuring it
     * remains valid until the renderer is done with it.
     *
     * @param surface The reference-counted surface to draw on.
     */
    internal fun setSurface(surface: SharedSurface) {
        synchronized(frameLock) {
            sharedSurface?.release()
            surface.acquire()
            sharedSurface = surface

            cppSetSurface(surface.surface, cppPointer)
            isAttached = true
        }
        start()
    }

    /**
     * Marks the animation as stopped.
     *
     * Lets the underlying renderer know we are intending to stop animating. We
     * will also not draw on the next draw cycle, and we will stop scheduling
     * [FrameCallbacks][Choreographer.FrameCallback].
     *
     * Note: Safe to call from the animation thread. e.g inside [draw]/[advance] callbacks.
     *
     * Note: If you can, call [stop] instead to avoid running multiple callback loops.
     */
    @CallSuper
    internal fun stopThread() {
        if (!isPlaying) return
        if (!hasCppObject) return
        // Prevent any other frame to be scheduled.
        isPlaying = false
        cppStop(cppPointer)
    }

    /**
     * Calls [stopThread] and removes any pending [FrameCallbacks][Choreographer.FrameCallback] from
     * the Choreographer.
     *
     * Note: this is **not** safe to call from the animation thread. e.g inside [draw]/[advance]
     * callbacks.
     */
    @CallSuper
    fun stop() {
        stopThread()
        removeFrameCallback()
    }

    /**
     * Destroys the held surface asynchronously by enqueuing destruction in the C++ JNIRenderer.
     * Also halts further frame callbacks to break the Choreographer loop.
     */
    internal fun destroySurfaceAsync() {
        synchronized(frameLock) {
            RiveLog.d(TAG) { "Surface destroy requested" }
            destroySurfaceLocked()
        }
        removeFrameCallback()
    }

    /**
     * Implementation of destroying the surface. Called either by:
     * - [onSurfaceTextureDestroyed][app.rive.runtime.kotlin.RiveTextureView.onSurfaceTextureDestroyed]
     *   -> [destroySurfaceAsync] or
     * - [onDetachedFromWindow][app.rive.runtime.kotlin.RiveTextureView.onDetachedFromWindow] ->
     *   [delete]
     *
     * ⚠️ Must be called within `frameLock`, hence the `Locked` part of the name.
     *
     * Performs the following:
     * - Marks the isAttached state as false, gating play and frame operations
     * - Stops the JNIRenderer
     * - Enqueues the surface destruction in the C++ JNIRenderer
     * - Releases and nulls the Kotlin reference to the surface
     */
    private fun destroySurfaceLocked() {
        isAttached = false
        stopThread()
        if (hasCppObject) {
            cppDestroySurface(cppPointer)
        }
        sharedSurface?.release()
        sharedSurface = null
    }

    /** Schedule a new frame callback to the Choreographer loop, calling back to [doFrame]. */
    open fun scheduleFrame() {
        Handler(Looper.getMainLooper()).post { // postFrameCallback must be called from the main looper
            Choreographer.getInstance().postFrameCallback(this@Renderer)
        }
    }

    /** Remove the active frame callback, breaking the Choreographer loop. */
    private fun removeFrameCallback() {
        Handler(Looper.getMainLooper()).post { // postFrameCallback must be called from the main looper
            Choreographer.getInstance().removeFrameCallback(this@Renderer)
        }
    }

    fun save() {
        cppSave(cppPointer)
    }

    fun restore() {
        cppRestore(cppPointer)
    }

    val width: Float
        get() = cppWidth(cppPointer).toFloat()

    val height: Float
        get() = cppHeight(cppPointer).toFloat()

    val averageFps: Float
        get() = cppAvgFps(cppPointer)

    fun align(
        fit: Fit,
        alignment: Alignment,
        targetBounds: RectF,
        sourceBounds: RectF,
        scaleFactor: Float = 1.0f,
    ) {
        cppAlign(
            cppPointer,
            fit,
            alignment,
            targetBounds,
            sourceBounds,
            scaleFactor
        )
    }

    fun transform(x: Float, sy: Float, sx: Float, y: Float, tx: Float, ty: Float) {
        cppTransform(cppPointer, x, sy, sx, y, tx, ty)
    }

    fun scale(sx: Float, sy: Float) {
        transform(sx, 0f, 0f, sy, 0f, 0f)
    }

    fun translate(dx: Float, dy: Float) {
        transform(1f, 0f, 0f, 1f, dx, dy)
    }

    @CallSuper
    override fun doFrame(frameTimeNanos: Long) {
        if (isPlaying && isAttached) {
            // This `synchronized` block ensures that the `delete()` method (called on the UI thread)
            // cannot proceed while a frame is being processed on the render thread.
            // It prevents a race where `cppPointer` could be nullified while `cppDoFrame` is
            // being executed.
            synchronized(frameLock) {
                // We must re-check `hasCppObject` inside the lock. It's possible for `delete()`
                // to have acquired the lock, nullified the pointer, and released the lock just
                // before this thread acquired it. This check prevents us from using a null pointer.
                if (hasCppObject && isPlaying && isAttached) {
                    cppDoFrame(cppPointer)
                }
            }
            // Check `isPlaying` again, as stop() could have been called during cppDoFrame.
            if (isPlaying && isAttached) {
                scheduleFrame()
            }
        }
    }


    /**
     * Schedules the deletion of the underlying C++ object using a two-phase disposal pattern.
     *
     * UI Thread: Immediately marks the Kotlin object as disposed to prevent new operations.
     * [cppDelete] schedules the actual deletion on the background render thread, ensuring
     * the C++ object is deleted only after all work for this Renderer has completed.
     *
     * Background Thread: cleans up resources after all pending work completes via
     * [disposeDependencies].
     */
    @CallSuper
    open fun delete() {
        stop()
        // Acquire the `frameLock`: If the render thread is currently inside `doFrame`, this call
        // will block until that frame completes. Once this lock is acquired, we have a guarantee
        // that no part of `doFrame` is executing.
        synchronized(frameLock) {
            destroySurfaceLocked()

            // We manually manage disposal rather than using `release()` here because we want
            // our dependencies to be cleaned up on the background render thread
            // (i.e. inside `disposeDependencies()`) rather than immediately on the UI thread,
            // which would invalidate resources that the render thread might still need.

            // Schedule the asynchronous disposal of the C++ JNIRenderer object.
            cppDelete(cppPointer)

            // Immediately nullify the pointer on the UI thread. This marks the
            // Kotlin object as disposed, preventing further attempts to use it
            // (e.g., in rapid view re-attachment scenarios).
            cppPointer = NULL_POINTER
        }
    }


    /**
     * Releases all of this renderer's dependents.
     *
     * This is called from the C++ worker thread as part of the asynchronous disposal.
     */
    @WorkerThread
    protected open fun disposeDependencies() {
        synchronized(frameLock) {
            sharedSurface?.release()
            sharedSurface = null
            dependencies.forEach { it.release() }
            dependencies.clear()
        }
    }
}