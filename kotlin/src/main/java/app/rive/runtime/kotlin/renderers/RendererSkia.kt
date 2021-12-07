package app.rive.runtime.kotlin.renderers

import android.view.Choreographer
import android.view.Surface
import androidx.annotation.CallSuper
import app.rive.runtime.kotlin.core.AABB
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit

abstract class RendererSkia(trace: Boolean = false) :
    BaseRenderer(),
    Choreographer.FrameCallback {
    final override var cppPointer: Long = constructor(trace)

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
        targetBoundsPointer: Long,
        srcBoundsPointer: Long
    )

    /** Instantiates JNIRendererSkia in C++ */
    private external fun constructor(trace: Boolean): Long

    var isPlaying: Boolean = false
        private set

    abstract fun draw()
    abstract fun advance(elapsed: Float)

    // Starts rendering thread (i.e. starts rendering frames)
    fun start() {
        if (isPlaying) return
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

    // Stop rendering thread.
    @CallSuper
    fun stop() {
        if (!isPlaying) return
        // Prevent any other frame to be scheduled.
        isPlaying = false
        cppStop(cppPointer)
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
        Choreographer.getInstance().removeFrameCallback(this)
        cleanupJNI(cppPointer)
        cppPointer = 0
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


    fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB) {
        cppAlign(
            cppPointer,
            fit,
            alignment,
            targetBounds.cppPointer,
            sourceBounds.cppPointer
        )
    }

    @CallSuper
    override fun doFrame(frameTimeNanos: Long) {
        // Draw.
        if (isPlaying) {
            cppDoFrame(cppPointer, frameTimeNanos)
            // Schedule a new frame: loop.
            scheduleFrame()
        }
    }
}