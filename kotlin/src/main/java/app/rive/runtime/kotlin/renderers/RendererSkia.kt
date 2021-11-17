package app.rive.runtime.kotlin.renderers

import android.util.Log

abstract class RendererSwappy : BaseRenderer() {
    final override var cppPointer: Long = constructor()

    external override fun cleanupJNI(cppPointer: Long)
    private external fun cppStop(rendererPointer: Long)
    private external fun cppStart(rendererPointer: Long)
    private external fun cppClearSurface(rendererPointer: Long)

    /** Instantiates JNIRendererSkia in C++ */
    private external fun constructor(): Long
    val address: Long = cppPointer

    abstract fun draw()
    abstract fun advance(elapsed: Float)

    // Starts rendering thread (i.e. starts rendering frames)
    fun start() {
        cppStart(cppPointer)
    }

    // Stop rendering thread.
    fun stop() {
        cppStop(cppPointer)
    }

    fun clearSurface() {
        cppClearSurface(cppPointer)
    }

    /**
     * Remove the [Renderer] object from memory.
     */
    fun cleanup() {
        cleanupJNI(cppPointer)
        cppPointer = 0
    }
}