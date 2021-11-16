package app.rive.runtime.kotlin.renderers

import android.util.Log
import app.rive.runtime.kotlin.RiveDrawable
import app.rive.runtime.kotlin.core.*

class ArtboardRenderer(private val artboardProvider: RiveDrawable) : RendererSwappy() {
    override fun draw() {
        artboardProvider.activeArtboard?.drawSkia(
            address,
            artboardProvider.fit,
            artboardProvider.alignment
        )
    }

    override fun advance(elapsed: Float) {
        if (!artboardProvider.advance(elapsed)) {
            stop()
        }
    }
}

abstract class RendererSwappy : BaseRenderer() {
    final override var cppPointer: Long = constructor()

    external override fun cleanupJNI(cppPointer: Long)
    private external fun cppStop(rendererPointer: Long)
    private external fun cppStart(rendererPointer: Long)

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

}