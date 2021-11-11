package app.rive.runtime.kotlin.renderers

import android.util.Log
import app.rive.runtime.kotlin.core.*

class RendererSkia : BaseRenderer() {
    override var cppPointer: Long = constructor()

    external override fun cleanupJNI(cppPointer: Long)
    external override fun cppDraw(artboardPointer: Long, rendererPointer: Long)

    private external fun constructor(): Long

    private val rivePlayer = RivePlayer()

    val address: Long = cppPointer

    companion object {
        // Static Tag for Logging
        const val TAG = "RendererSkia"
    }

    override fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB) {
        rivePlayer.fit = fit
        rivePlayer.alignment = alignment
        rivePlayer.targetBounds = targetBounds
    }

    fun setSize(width: Float, height: Float) {
        rivePlayer.targetBounds = AABB(width, height)
    }

    fun setFit(fit: Fit) {
        rivePlayer.fit = fit
    }

    fun setAlignment(alignment: Alignment) {
        rivePlayer.alignment = alignment
    }

    fun play(animationName: String) {
        rivePlayer.play(animationName)
    }

    fun addArtboard(artboard: Artboard) {
        rivePlayer.addArtboard(artboard)
    }

    fun draw() {
        rivePlayer.activeArtboard?.drawSkia(
            this,
            rivePlayer.fit,
            rivePlayer.alignment
        )
    }

    fun advance(elapsed: Float) {
        rivePlayer.advance(elapsed)
    }

    override fun draw(artboard: Artboard) {
//        startFrame(cppPointer)
        cppDraw(artboard.cppPointer, cppPointer)
//        var start = SystemClock.elapsedRealtimeNanos()
//        artboard.drawSkia(this)
//        val now = SystemClock.elapsedRealtimeNanos()
//        Log.d("SKIA DRAW", "Frame: ${(now - start) / 1000000} ms")
    }


    private class RivePlayer {
        companion object {
            const val TAG = "RivePlayer"
        }

        var activeArtboard: Artboard? = null
        var activeAnimations = mutableListOf<LinearAnimationInstance>()

        var targetBounds = AABB(0f, 0f)
            set(value) {
                if (value != field) {
                    field = value
                }
            }
        var fit = Fit.CONTAIN
            set(value) {
                if (value != field) {
                    field = value
                }
            }
        var alignment = Alignment.CENTER
            set(value) {
                if (value != field) {
                    field = value
                }
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

        fun advance(elapsed: Float) {
            activeArtboard?.let { artboard ->
                activeAnimations.forEach { aInstance ->
                    aInstance.advance(elapsed)
                    aInstance.apply(artboard)
                }
                artboard.advance(elapsed)
            }
        }
    }
}