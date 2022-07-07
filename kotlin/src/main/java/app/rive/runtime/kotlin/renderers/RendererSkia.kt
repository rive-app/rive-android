package app.rive.runtime.kotlin.renderers

import android.view.Choreographer
import android.view.Surface
import androidx.annotation.CallSuper
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit

import android.graphics.RectF

abstract class RendererSkia(private val trace: Boolean = false) :
    BaseRenderer(),
    Choreographer.FrameCallback {
    override var cppPointer: Long = constructor(trace)

    external override fun cleanupJNI(cppPointer: Long)
    private external fun cppStart(rendererPointer: Long)
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

    fun make() {
        if (cppPointer == 0L) {
            cppPointer = constructor(trace)
        }
    }

    var isPlaying: Boolean = false
        private set

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
        if (cppPointer == 0L) {
            return
        }
        isPlaying = true
        cppStart(cppPointer)
        // Register for a new frame.
        scheduleFrame()
    }

    fun setSurface(surface: Surface) {
        cppSetSurface(surface, cppPointer)
        // Register for a new frame.
        cppDoFrame(cppPointer, 0)
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
        if (cppPointer == 0L) {
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
    }

    /**
     * Remove the [Renderer] object from memory.
     */
    @CallSuper
    fun cleanup() {
        clearSurface()
        // Queues the cpp Renderer for deletion
        cleanupJNI(cppPointer)
        // Mark the underlying object as deleted right away:
        //  this object is scheduled for deletion and shouldn't be used anymore.
        cppPointer = 0L
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
            cppDoFrame(cppPointer, frameTimeNanos)
            scheduleFrame()
        }
    }
}