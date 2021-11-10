package app.rive.runtime.kotlin.renderers

import android.util.Log
import app.rive.runtime.kotlin.core.*

class RendererSkia : BaseRenderer() {
    override var cppPointer: Long = constructor()

    external override fun cleanupJNI(cppPointer: Long)
    external override fun cppDraw(artboardPointer: Long, rendererPointer: Long)

    private external fun constructor(): Long
    private external fun startFrame(cppPointer: Long)
    private external fun initializeSkiaGL(cppPointer: Long)
    private external fun setViewport(cppPointer: Long, width: Int, height: Int)

    private var activeArtboard: Artboard? = null
    private var activeAnimations = mutableListOf<LinearAnimationInstance>()

    val address: Long = cppPointer

    companion object {
        // Static Tag for Logging
        const val TAG = "RendererSkia"
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

    fun play(animationName: String) {
        activeArtboard?.let {
            val animation = it.animation(animationName)
            val instance = LinearAnimationInstance(animation).also { it.advance(0.0f) }
            activeAnimations.add(instance)
        } ?: run {
            Log.w(TAG, "Can't play animation $animationName without an active Artboard")
        }
    }

    fun addArtboard(artboard: Artboard) {
        val instance = artboard.getInstance()
        instance.advance(0.0f)
        activeArtboard = artboard.getInstance().also { it.advance(0.0f) }
    }

    fun draw() {
        activeArtboard?.drawSkia(this)
    }

    fun advance(elapsed: Float) {
        activeArtboard?.let { artboard ->
            activeAnimations.forEach { aInstance ->
                aInstance.advance(elapsed)
                aInstance.apply(artboard)
            }
            artboard.advance(elapsed)
        }
    }

    override fun draw(artboard: Artboard) {
        startFrame(cppPointer)
        cppDraw(artboard.cppPointer, cppPointer)
//        var start = SystemClock.elapsedRealtimeNanos()
//        artboard.drawSkia(this)
//        val now = SystemClock.elapsedRealtimeNanos()
//        Log.d("SKIA DRAW", "Frame: ${(now - start) / 1000000} ms")
    }
}