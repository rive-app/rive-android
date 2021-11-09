package app.rive.runtime.kotlin.core

import android.graphics.Canvas

class RendererOpenGL : BaseRenderer() {
    override external fun cleanupJNI(cppPointer: Long)
    private external fun constructor(): Long
    private external fun startFrame(cppPointer: Long)
    private external fun initializeGL(cppPointer: Long)
    private external fun setViewport(cppPointer: Long, width: Int, height: Int)

    override var cppPointer: Long = constructor()

    private external fun cppDraw(artboardPointer: Long, rendererPointer: Long)

    /**
     * Initialize OpenGL Renderer in C++
     */
    fun initializeGL() {
        initializeGL(cppPointer)
    }

    fun setViewport(width: Int, height: Int) {
        setViewport(cppPointer, width, height)
    }

    fun draw(artboard: Artboard) {
        startFrame(cppPointer)
        cppDraw(artboard.cppPointer, this.cppPointer)
    }


    override fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB) {}

    override fun draw(artboard: Artboard, canvas: Canvas) {
        startFrame(cppPointer)
        cppDraw(artboard.cppPointer, this.cppPointer)
    }


    private fun fileCleanup() {
        // TODO:
    }
}