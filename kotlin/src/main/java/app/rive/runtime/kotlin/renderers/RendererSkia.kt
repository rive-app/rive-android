package app.rive.runtime.kotlin.renderers

import android.view.Choreographer
import android.view.Surface
import androidx.annotation.CallSuper

abstract class RendererSkia : BaseRenderer(), Choreographer.FrameCallback {
    final override var cppPointer: Long = constructor()

    external override fun cleanupJNI(cppPointer: Long)
    private external fun cppStart(rendererPointer: Long)
    private external fun cppStop(rendererPointer: Long)
    private external fun cppDoFrame(rendererPointer: Long)
    private external fun cppSetSurface(surface: Surface, rendererPointer: Long)
    private external fun cppClearSurface(rendererPointer: Long)

    /** Instantiates JNIRendererSkia in C++ */
    private external fun constructor(): Long

    abstract var isPlaying: Boolean

    abstract fun draw()
    abstract fun advance(elapsed: Float)

    // Starts rendering thread (i.e. starts rendering frames)
    fun start() {
        cppStart(cppPointer)
    }

    fun setSurface(surface: Surface) {
        cppSetSurface(surface, cppPointer)
    }

    // Stop rendering thread.
    @CallSuper
    fun stop() {
        cppStop(cppPointer)
    }

    private fun clearSurface() {
        cppClearSurface(cppPointer)
    }

    /**
     * Remove the [Renderer] object from memory.
     */
    @CallSuper
    fun cleanup() {
        // Prevent any other frame to be scheduled.
        isPlaying = false
        clearSurface()
        cleanupJNI(cppPointer)
        cppPointer = 0
    }

    @CallSuper
    override fun doFrame(frameTimeNanos: Long) {
        if (isPlaying) {
            cppDoFrame(cppPointer)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}