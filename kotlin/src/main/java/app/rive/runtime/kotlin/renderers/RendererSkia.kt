package app.rive.runtime.kotlin.renderers

import android.view.Choreographer
import android.view.Surface
import androidx.annotation.CallSuper

abstract class RendererSkia :
    BaseRenderer(),
    Choreographer.FrameCallback {
    final override var cppPointer: Long = constructor()

    external override fun cleanupJNI(cppPointer: Long)
    private external fun cppStart(rendererPointer: Long)
    private external fun cppStop(rendererPointer: Long)
    private external fun cppDoFrame(rendererPointer: Long, frameTimeNanos: Long)
    private external fun cppSetSurface(surface: Surface, rendererPointer: Long)
    private external fun cppClearSurface(rendererPointer: Long)

    /** Instantiates JNIRendererSkia in C++ */
    private external fun constructor(): Long

    var isPlaying: Boolean = false

    abstract fun draw()
    abstract fun advance(elapsed: Float)

    // Starts rendering thread (i.e. starts rendering frames)
    fun start() {
        isPlaying = true
        cppStart(cppPointer)
        // Register for a new frame.
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun setSurface(surface: Surface) {
        cppSetSurface(surface, cppPointer)
        // Register for a new frame.
        cppDoFrame(cppPointer, 0)
    }

    // Stop rendering thread.
    @CallSuper
    fun stop() {
        // Prevent any other frame to be scheduled.
        isPlaying = false
        cppStop(cppPointer)
    }

    private fun clearSurface() {
        if (isPlaying) {
            stop()
        }
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

    protected fun finalize() {
    }

    @CallSuper
    override fun doFrame(frameTimeNanos: Long) {
        // Draw.
        if (isPlaying) {
            cppDoFrame(cppPointer, frameTimeNanos)
            // Schedule a new frame: loop.
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}