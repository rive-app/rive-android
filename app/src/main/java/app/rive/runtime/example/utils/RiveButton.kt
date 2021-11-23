package app.rive.runtime.example.utils

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.widget.AppCompatImageButton
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.RiveArtboardRenderer
import app.rive.runtime.kotlin.RiveSurfaceView
import app.rive.runtime.kotlin.core.AABB
import app.rive.runtime.kotlin.core.File

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