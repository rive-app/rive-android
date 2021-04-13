package app.rive.runtime.kotlin

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import app.rive.runtime.kotlin.core.*

class RiveDrawable : Drawable(), Animatable {

    private val animator = TimeAnimator()
    private val renderer = Renderer()
    private var file: File? = null
    private var artboard: Artboard? = null
    private val animations = mutableListOf<LinearAnimationInstance>()
    private var targetBounds: AABB

    init {
        targetBounds = AABB(bounds.width().toFloat(), bounds.height().toFloat())
        animator.setTimeListener { animation, total, delta ->
            artboard?.let { ab ->
                val elapsed = delta.toFloat() / 1000
                animations.forEach {
                    it.advance(elapsed)
                    it.apply(ab, 1f)
                }
                ab.advance(elapsed)
            }
            invalidateSelf()
        }
    }

    fun setAnimationFile(file: File) {
        this.file = file
        val artboard = file.artboard.also {
            this.artboard = it
        }
        val animationCount = artboard.animationCount

        for (i in 0 until animationCount) {
            val animation = artboard.animation(i)
            animations.add(LinearAnimationInstance(animation))
        }
    }

    override fun onBoundsChange(bounds: Rect?) {
        super.onBoundsChange(bounds)

        bounds?.let {
            targetBounds = AABB(bounds.width().toFloat(), bounds.height().toFloat())
        }
    }

    private var boundsCache: Rect? = null
    override fun draw(canvas: Canvas) {
        artboard?.let { ab ->

            if (boundsCache != bounds) {
                boundsCache = bounds
                targetBounds = AABB(bounds.width().toFloat(), bounds.height().toFloat())
            }

            renderer.canvas = canvas
            renderer.align(Fit.CONTAIN, Alignment.CENTER, targetBounds, ab.bounds)
            val saved = canvas.save()
            ab.draw(renderer)
            canvas.restoreToCount(saved)
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    fun setRepeatMode(mode: Loop) {

        when (mode) {
            Loop.ONESHOT -> {
                animator.repeatCount = 1
            }
            Loop.LOOP -> {
                animator.repeatMode = ValueAnimator.RESTART
            }
            Loop.PINGPONG -> {
                animator.repeatMode = ValueAnimator.REVERSE
            }
            Loop.NONE -> {
                // TODO: handle this
            }
        }

        animations.forEach {
            it.animation.loop = mode
        }
    }

    override fun getIntrinsicWidth(): Int {
        return artboard?.bounds?.width?.toInt() ?: -1
    }

    override fun getIntrinsicHeight(): Int {
        return artboard?.bounds?.height?.toInt() ?: -1
    }

    fun reset() {
        animator.cancel()
        animator.currentPlayTime = 0

        animations.forEach {
            if (it.animation.workStart != -1) {
                it.time(it.animation.workStart.toFloat());
            } else {
                it.time(0f);
            }
            artboard?.let { ab ->
                it.apply(ab, 1f)
                ab.advance(0f)
            }
        }

        invalidateSelf()
    }

    fun pause() {
        animator.pause()
    }

    override fun start() {
        animator.start()
    }

    override fun stop() {
        animator.cancel()
    }

    override fun isRunning(): Boolean {
        return (animator.isRunning && !animator.isPaused)
    }

    fun destroy() {
        renderer.cleanup()
    }
}