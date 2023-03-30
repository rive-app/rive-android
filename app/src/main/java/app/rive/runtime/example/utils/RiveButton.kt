package app.rive.runtime.example.utils

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import app.rive.runtime.kotlin.RiveAnimationView

class RiveButton(context: Context, attrs: AttributeSet? = null) :
    RiveAnimationView(context, attrs) {

    private var pressAnimation: String?
    override val defaultAutoplay = true

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            app.rive.runtime.example.R.styleable.RiveButton,
            0, 0
        ).apply {
            try {
                pressAnimation =
                    getString(app.rive.runtime.example.R.styleable.RiveButton_rivePressAnimation)
            } finally {
                recycle()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Pass through for performing click
        when (event?.action) {
            MotionEvent.ACTION_UP -> performClick()
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        pressAnimation?.let {
            artboardRenderer?.stopAnimations()
            artboardRenderer?.play(it)
            return true
        } ?: run {
            artboardRenderer?.stopAnimations()
            artboardRenderer?.play()
        }
        return super.performClick()
    }
}