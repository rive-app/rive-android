package app.rive.runtime.kotlin.renderers

import android.util.Log
import app.rive.runtime.kotlin.core.*

class RendererSkia : BaseRenderer() {
    override var cppPointer: Long = constructor()

    external override fun cleanupJNI(cppPointer: Long)
    external override fun cppDraw(artboardPointer: Long, rendererPointer: Long)

    private external fun constructor(): Long

    private var activeArtboard: Artboard? = null
    private var activeAnimations = mutableListOf<LinearAnimationInstance>()

    private var mTargetBounds = AABB(0f, 0f)
    private var mFit = Fit.CONTAIN
        set(value) {
            if (value != field) {
                field = value
            }
        }
    private var mAlignment = Alignment.CENTER
        set(value) {
            if (value != field) {
                field = value
            }
        }

    val address: Long = cppPointer

    companion object {
        // Static Tag for Logging
        const val TAG = "RendererSkia"
    }

    override fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB) {
        mFit = fit
        mAlignment = alignment
        mTargetBounds = targetBounds
    }

    fun setSize(width: Float, height: Float) {
        mTargetBounds = AABB(width, height)
    }

    fun play(animationName: String) {
        activeArtboard?.let { artboard ->
            val animation = artboard.animation(animationName)
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
        activeArtboard?.drawSkia(this, mFit, mAlignment)
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
//        startFrame(cppPointer)
        cppDraw(artboard.cppPointer, cppPointer)
//        var start = SystemClock.elapsedRealtimeNanos()
//        artboard.drawSkia(this)
//        val now = SystemClock.elapsedRealtimeNanos()
//        Log.d("SKIA DRAW", "Frame: ${(now - start) / 1000000} ms")
    }
}