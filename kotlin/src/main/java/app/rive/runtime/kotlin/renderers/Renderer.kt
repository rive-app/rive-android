package app.rive.runtime.kotlin.renderers

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.NativeObject
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive

abstract class Renderer(
    @get:VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var type: RendererType = Rive.defaultRendererType,
    val trace: Boolean = false
) : NativeObject(NULL_POINTER),
    Choreographer.FrameCallback {
    // From NativeObject.
    external override fun cppDelete(pointer: Long)
    //

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
        srcBounds: RectF
    )

    private external fun cppTransform(
        cppPointer: Long,
        x: Float,
        sy: Float,
        sx: Float,
        y: Float,
        tx: Float,
        ty: Float
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
     * Helper function to reassign the renderer type.
     * This might be necessary if `constructor()` couldn't build the Renderer with `type`
     * but had to fall back on a different value (e.g. PLS isn't available on emulators and
     * the Renderer defaults back to Skia)
     */
    private fun setRendererType(newType: Int) {
        if (newType != type.value) {
            type = RendererType.fromIndex(newType)
        }
    }

    var isPlaying: Boolean = false
        private set
    var isAttached: Boolean = false

    @WorkerThread
    abstract fun draw()

    @WorkerThread
    abstract fun advance(elapsed: Float)

    /**
     * Starts the Renderer & registers for frameCallbacks
     *
     * Goal:
     * When we trigger start, doFrame gets called once per frame
     *   - until we stop
     *   - or the animation finishes
     *
     * Gotchas:
     * - scheduleFrame triggers callbacks to "doFrame" which in turn schedule more frames
     * - if we call scheduleFrame multiple times we enter multiple parallel animations loops
     * - to avoid this we check isPlaying & deregister frameCallbacks when stop is called by users
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

    fun setSurface(surface: Surface) {
        cppSetSurface(surface, cppPointer)
        isAttached = true
        start()
    }

    /**
     * Marks the animation as stopped
     *
     * lets the underlying renderer know we are intending to stop animating.
     * we will also not draw on the next drawCycle & stop scheduling frameCallbacks
     *
     * NOTE: safe to call from the animation thread.
     * e.g inside .draw() / .advance(elapsed: Float) callbacks
     *
     * NOTE: if you can, call stop() instead to avoid running multiple callback loops
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
     * Calls stop, and removes any pending frameCallbacks from the Choreographer
     *
     * NOTE: this is NOT safe to call from the animation thread.
     * e.g inside .draw() / .advance(elapsed: Float) callbacks
     */
    @CallSuper
    fun stop() {
        stopThread()
        Handler(Looper.getMainLooper()).post { // postFrameCallback must be called from the main looper
            Choreographer.getInstance().removeFrameCallback(this@Renderer)
        }
    }

    private fun destroySurface() {
        isAttached = false
        stop()
        cppDestroySurface(cppPointer)
    }

    open fun scheduleFrame() {
        Handler(Looper.getMainLooper()).post { // postFrameCallback must be called from the main looper
            Choreographer.getInstance().postFrameCallback(this@Renderer)
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


    fun align(fit: Fit, alignment: Alignment, targetBounds: RectF, sourceBounds: RectF) {
        cppAlign(
            cppPointer,
            fit,
            alignment,
            targetBounds,
            sourceBounds
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
        if (isPlaying) {
            cppDoFrame(cppPointer)
            scheduleFrame()
        }
    }

    /**
     * Trigger a delete of the underlying C++ object.
     *
     * [cppDelete] call will delete the underlying object.
     * This will internally trigger a call to [disposeDependencies]
     */
    @CallSuper
    open fun delete() {
        destroySurface()
        // Queues the cpp Renderer for deletion
        cppDelete(cppPointer)
        cppPointer = NULL_POINTER
    }

    /**
     * Deletes all this renderer's dependents.
     *
     * Called internally by the JNI within ~JNIRenderer()
     *
     * N.B. this function is marked as `protected` instead of `private` because
     * otherwise it's inaccessible from JNI on API < 24
     */
    protected open fun disposeDependencies() {
        dependencies.forEach { it.release() }
        dependencies.clear()
    }
}