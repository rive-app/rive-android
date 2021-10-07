package app.rive.runtime.kotlin.renderers

import android.graphics.Canvas
import app.rive.runtime.kotlin.core.AABB
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.Fit

class RendererOpenGL : BaseRenderer() {
    override var cppPointer: Long = constructor()

    external override fun cleanupJNI(cppPointer: Long)
    external override fun cppDraw(artboardPointer: Long, rendererPointer: Long)

    private external fun constructor(): Long
    private external fun startFrame(cppPointer: Long)
    private external fun initializeGL(cppPointer: Long)
    private external fun setViewport(cppPointer: Long, width: Int, height: Int)


    /**
     * Initialize OpenGL Renderer in C++
     */
    fun initializeGL() {
        initializeGL(cppPointer)
    }

    fun setViewport(width: Int, height: Int) {
        setViewport(cppPointer, width, height)
    }

    override fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB) {}

    override fun draw(artboard: Artboard) {
        startFrame(cppPointer)
        cppDraw(artboard.cppPointer, this.cppPointer)
    }

}