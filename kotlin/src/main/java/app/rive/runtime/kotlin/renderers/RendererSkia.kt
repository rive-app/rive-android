package app.rive.runtime.kotlin.renderers

import android.util.Log
import app.rive.runtime.kotlin.core.*

class RendererSkia : BaseRenderer() {
    override var cppPointer: Long = constructor()

    external override fun cleanupJNI(cppPointer: Long)
    external override fun cppDraw(artboardPointer: Long, rendererPointer: Long)
    private external fun cppStop(rendererPointer: Long)
    private external fun cppStart(rendererPointer: Long)

    private external fun constructor(): Long

    private val rivePlayer = RivePlayer()

    val address: Long = cppPointer
    val isPlaying: Boolean
        get() = rivePlayer.activeAnimations.isNotEmpty()

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

    // Starts rendering frames.
    fun start() {
        cppStart(cppPointer)
    }

    fun play(animationName: String) {
        rivePlayer.play(animationName)
        cppStart(cppPointer)
    }

    fun pause(animationName: String) {
        rivePlayer.pause(animationName)
    }

    fun stop(animationName: String) {
        rivePlayer.stop(animationName)
        if (rivePlayer.isPlaying) {
            cppStop(cppPointer)
        }
    }

    // Stop all animations.
    fun stop() {
        rivePlayer.activeAnimations.clear()
        cppStop(cppPointer)
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
        // TODO: revisit this abstraction.
    }

    protected fun finalize() {
        cleanupJNI(cppPointer)
    }

    private class RivePlayer {
        companion object {
            const val TAG = "RivePlayer"
        }

        var activeArtboard: Artboard? = null
        var activeAnimations = mutableListOf<PlayableInstance>()

        val isPlaying: Boolean
            get() = activeAnimations.filter { it.isPlaying }.isNotEmpty()

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
            // Try to restart animations first.
            activeAnimations.firstOrNull {
                it.playable.name == animationName
            }?.let {
                it.isPlaying = true
            } ?: run {
                activeArtboard?.let { artboard ->
                    val animation = artboard.animation(animationName)
                    val instance = LinearAnimationInstance(animation).also { it.advance(0.0f) }
                    activeAnimations.add(instance)
                } ?: run {
                    Log.w(TAG, "Can't play animation $animationName without an active Artboard")
                }
            }
        }

        fun pause(animationName: String) {
            activeAnimations.firstOrNull {
                it.playable.name == animationName
            }?.let {
                it.isPlaying = false
            }
        }

        fun stop(animationName: String): Boolean {
            val index = activeAnimations.indexOfFirst {
                it.playable.name == animationName
            }
            if (index >= 0) {
                activeAnimations.removeAt(index)
            }
            return index >= 0
        }

        fun addArtboard(artboard: Artboard) {
            val instance = artboard.getInstance()
            instance.advance(0.0f)
            activeArtboard = artboard.getInstance().also { it.advance(0.0f) }
        }

        fun advance(elapsed: Float) {
            activeArtboard?.let { artboard ->
                activeAnimations.filter { it.isPlaying }.forEach { instance ->
                    instance.apply(artboard, elapsed)
                }
                artboard.advance(elapsed)
            }
        }
    }
}