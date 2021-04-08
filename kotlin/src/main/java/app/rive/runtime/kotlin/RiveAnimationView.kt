package app.rive.runtime.kotlin

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.RawRes
import androidx.appcompat.widget.AppCompatImageView

class RiveAnimationView : AppCompatImageView {

    private var drawable: RiveDrawable? = null
    val isRunning: Boolean
        get() = drawable?.isRunning ?: false

    constructor(context: Context) : super(context)

    // TODO: Add attrs to use in xml layout
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    // TODO: add async
    fun setAnimation(@RawRes resId: Int) {
        drawable?.run {
            reset()
            destroy()
        }
        drawable = RiveDrawable().apply {
            val file = File(resources.openRawResource(resId).readBytes())
            setAnimationFile(file)
            setImageDrawable(this)
        }
        requestLayout()
    }

    fun setRepeatMode(mode: Loop) {
        drawable?.setRepeatMode(mode)
    }

    fun reset() {
        drawable?.reset()
    }

    fun start() {
        drawable?.start()
    }

    fun pause() {
        drawable?.pause()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
    }
}