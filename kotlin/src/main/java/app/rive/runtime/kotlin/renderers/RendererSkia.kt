package app.rive.runtime.kotlin.renderers

import android.graphics.RectF
import android.view.Choreographer
import android.view.Surface
import androidx.annotation.CallSuper
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.NativeObject

abstract class RendererSkia(private val trace: Boolean = false) :
    NativeObject(NULL_POINTER),
    Choreographer.FrameCallback {
    // From NativeObject.
    external override fun cppDelete(pointer: Long)
    //

    private external fun cppStart(rendererPointer: Long, timeNanos: Long)
    private external fun cppStop(rendererPointer: Long)
    private external fun cppSave(rendererPointer: Long)
    private external fun cppRestore(rendererPointer: Long)
    private external fun cppWidth(rendererPointer: Long): Int
    private external fun cppHeight(rendererPointer: Long): Int
    private external fun cppAvgFps(rendererPointer: Long): Float
    private external fun cppDoFrame(rendererPointer: Long, frameTimeNanos: Long)
    private external fun cppSetSurface(surface: Surface, rendererPointer: Long)
    private external fun cppClearSurface(rendererPointer: Long)
    private external fun cppAlign(
        cppPointer: Long,
        fit: Fit,
        alignment: Alignment,
        targetBounds: RectF,
        srcBounds: RectF
    )

    /** Instantiates JNIRendererSkia in C++ */
    private external fun constructor(trace: Boolean): Long

    @CallSuper
    open fun make() {
        if (!hasCppObject) {
            cppPointer = constructor(trace)
        }
    }

    var isPlaying: Boolean = false
        private set
    var isAttached: Boolean = false
        protected set

    abstract fun draw()
    abstract fun advance(elapsed: Float)

    /**
     * Starts the SkiaRenderer & registers for frameCallbacks
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
        if (!hasCppObject) {
            return
        }
        val nanoTime = System.nanoTime()
        isPlaying = true
        cppStart(cppPointer, nanoTime)
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
        if (!hasCppObject) {
            return
        }
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
        Choreographer.getInstance().removeFrameCallback(this)
    }

    private fun clearSurface() {
        stop()
        cppClearSurface(cppPointer)
        isAttached = false
    }

    open fun scheduleFrame() {
        Choreographer.getInstance().postFrameCallback(this)
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

    @CallSuper
    override fun doFrame(frameTimeNanos: Long) {
        if (isPlaying) {
            val nanoTime = System.nanoTime()
            cppDoFrame(cppPointer, nanoTime)
            scheduleFrame()
        }
    }

    /**
     * Trigger a delete of the underlying C++ object.
     *
     * [cppDelete] call will enqueue a call to delete the underlying C++ object.
     * This is to allow the current rendering thread to drain its queue before deleting dependencies.
     * This will internally trigger a call to [disposeDependencies]
     */
    @CallSuper
    open fun delete() {
        clearSurface()
        // Queues the cpp Renderer for deletion
        cppDelete(cppPointer)
        cppPointer = NULL_POINTER
    }

    /**
     * Deletes all this renderer's dependents.
     *
     * Called internally by the JNI - Only once the thread work queue has been drained and we
     * don't risk using dangling pointers of any dependency (e.g. Artboards or Animation Instances)
     *
     * N.B. this function is marked as `protected` instead of `private` because
     * otherwise it's inaccessible from JNI on API < 24
     */
    protected open fun disposeDependencies() {
        dependencies.forEach { it.dispose() }
        dependencies.clear()
    }
}