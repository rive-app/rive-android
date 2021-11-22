package app.rive.runtime.example.utils

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import app.rive.runtime.kotlin.RiveArtboardRenderer
import app.rive.runtime.kotlin.core.File

class RiveButton(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageButton(context, attrs) {
    var riveArtboardRenderer: RiveArtboardRenderer;
    var pressAnimation: String?;

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            app.rive.runtime.example.R.styleable.RiveButton,
            0, 0
        ).apply {
            try {
                val resourceId =
                    getResourceId(app.rive.runtime.example.R.styleable.RiveButton_riveResource, -1)
                pressAnimation =
                    getString(app.rive.runtime.example.R.styleable.RiveButton_rivePressAnimation)

                var resourceBytes = resources.openRawResource(resourceId).readBytes()
                var riveFile = File(resourceBytes)
                riveArtboardRenderer = RiveArtboardRenderer(autoplay = false)
                riveArtboardRenderer.setRiveFile(riveFile)
//                background = riveDrawable

            } finally {
                recycle()
            }
        }

    }

    override fun performClick(): Boolean {
        pressAnimation?.let {
            riveArtboardRenderer.stopAnimations()
            riveArtboardRenderer.play(it)
            return true
        } ?: run {
            riveArtboardRenderer.stopAnimations()
            riveArtboardRenderer.play()
        }
        return super.performClick()
    }


}