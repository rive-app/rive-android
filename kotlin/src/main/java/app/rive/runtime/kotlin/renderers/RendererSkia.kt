package app.rive.runtime.kotlin.renderers

import app.rive.runtime.kotlin.core.AABB
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.Fit

class RendererSkia : BaseRenderer() {
    override var cppPointer: Long = constructor()

    override external fun cleanupJNI(cppPointer: Long)

    override external fun cppDraw(artboardPointer: Long, rendererPointer: Long)

    private external fun constructor(): Long
    private external fun startFrame(cppPointer: Long)
    private external fun initializeSkiaGL(cppPointer: Long)
    private external fun setViewport(cppPointer: Long, width: Int, height: Int)
    private external fun nSetArtboard(cppPointer: Long, artboardPointer: Long)

    val address: Long = cppPointer

    var artboard: Artboard? = null
        get() = field
        set(value) {
            if (value == field) return
            field = value
            nSetArtboard(cppPointer, field?.cppPointer ?: 0)
        }

    fun initializeSkia() {
        initializeSkiaGL(cppPointer)
    }

    fun setViewport(width: Int, height: Int) {
        setViewport(cppPointer, width, height)
    }

    override fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB) {
        // NOP
        // TODO: reconsider this in place of setViewport?
    }

    fun startFrame() {
        startFrame(cppPointer)
    }

    override fun draw(artboard: Artboard) {
        // TODO: not sure we need to clear the background every frame?
        startFrame(cppPointer)
        cppDraw(artboard.cppPointer, cppPointer)
//        var start = SystemClock.elapsedRealtimeNanos()
//        artboard.drawSkia(this)
//        val now = SystemClock.elapsedRealtimeNanos()
//        Log.d("SKIA DRAW", "Frame: ${(now - start) / 1000000} ms")
    }
}