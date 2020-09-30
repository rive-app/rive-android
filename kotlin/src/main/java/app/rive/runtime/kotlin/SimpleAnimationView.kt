package app.rive.runtime.kotlin

import android.content.Context
import android.graphics.Canvas
import android.view.View


class SimpleAnimationView : View {
    private var lastTime: Long = 0

    val renderer: Renderer
    val artboard: Artboard
    val animationInstances: ArrayList<LinearAnimationInstance>

    lateinit var targetBounds: AABB
    var isPlaying = true
        get() = field
        set(value) {
            if (value != field) {
                field = value
                if (value) {
                    lastTime = System.currentTimeMillis()
                    invalidate()
                }
            }
        }

    constructor(_renderer: Renderer, _artboard: Artboard, context: Context) : super(context) {
        lastTime = System.currentTimeMillis()
        renderer = _renderer
        artboard = _artboard
        animationInstances = ArrayList<LinearAnimationInstance>()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        redraw(canvas)
    }

    fun redraw(canvas: Canvas) {
        val currentTime = System.currentTimeMillis()
        val elapsed = (currentTime - lastTime) / 1000f
        lastTime = currentTime
        renderer.canvas = canvas
        renderer.align(Fit.CONTAIN, Alignment.CENTER, targetBounds, artboard.bounds())

        animationInstances.forEach {
            it.advance(elapsed)
            it.apply(artboard, 1f)
        }

        canvas.save()
        artboard.advance(elapsed)
        artboard.draw(renderer, canvas)
        canvas.restore()

        if (isPlaying) {
            // Paint again.
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh);
        targetBounds = AABB(w.toFloat(), h.toFloat());
        invalidate()
    }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer.cleanup()
    }
}