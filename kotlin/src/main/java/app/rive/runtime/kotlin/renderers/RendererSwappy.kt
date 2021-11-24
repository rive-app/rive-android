package app.rive.runtime.kotlin.renderers

import android.view.Surface
import androidx.annotation.CallSuper

abstract class RendererSwappy : BaseRenderer() {
    final override var cppPointer: Long = constructor()

    external override fun cleanupJNI(cppPointer: Long)
    private external fun cppStop(rendererPointer: Long)
    private external fun cppStart(rendererPointer: Long)
    private external fun cppSetSurface(surface: Surface, rendererPointer: Long)
    private external fun cppClearSurface(rendererPointer: Long)

    /** Instantiates JNIRendererSkia in C++ */
    private external fun constructor(): Long

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
    fun cleanup() {
        stop()
        clearSurface()
        cleanupJNI(cppPointer)
        cppPointer = 0
    }
}