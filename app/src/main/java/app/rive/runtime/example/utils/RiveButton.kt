package app.rive.runtime.example.utils

import android.content.Context
import android.util.AttributeSet
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

    override fun performClick(): Boolean {
        pressAnimation?.let {
            renderer.stopAnimations()
            renderer.play(it)
            return true
        } ?: run {
            renderer.stopAnimations()
            renderer.play()
        }
        return super.performClick()
    }
}