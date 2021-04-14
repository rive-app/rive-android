package app.rive.runtime.kotlin

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.RawRes
import app.rive.runtime.kotlin.core.*

class RiveAnimationView : View {
    private var autoplay: Boolean = true;
    private var fit: Fit = Fit.CONTAIN
    private var loop: Loop = Loop.LOOP
    private var alignment: Alignment = Alignment.CENTER
    private var drawable: RiveDrawable = RiveDrawable(fit, alignment, loop);


    constructor(context: Context) : super(context) {
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.RiveAnimationView,
            0, 0
        ).apply {
            try {
                val alignmentIndex = getInteger(R.styleable.RiveAnimationView_riveAlignment, 4)
                alignment = Alignment.values()[alignmentIndex]

                val fitIndex = getInteger(R.styleable.RiveAnimationView_riveFit, 1)
                fit = Fit.values()[fitIndex]

                val loopIndex = getInteger(R.styleable.RiveAnimationView_riveLoop, 1)
                loop = Loop.values()[loopIndex]

                autoplay = getBoolean(R.styleable.RiveAnimationView_riveAutoPlay, true)

                val resourceId = getResourceId(R.styleable.RiveAnimationView_riveResource, -1)
                if (resourceId != -1) {
                    setRiveResource(resourceId)
                }

            } finally {
                recycle()
            }
        }
    }

    fun pause() {
        drawable.pause()
    }

    fun start() {
        drawable.start()
    }

    fun reset() {
        drawable.reset()
    }

    val isRunning: Boolean
        get() = drawable.isRunning

    fun setRiveResource(@RawRes resId: Int) {
        val file = File(resources.openRawResource(resId).readBytes())
        setAnimationFile(file)
    }

    fun setAnimationFile(file: File) {
        drawable.run {
            reset()
            destroy()
        }
        drawable = RiveDrawable(fit, alignment, loop).apply {
            setAnimationFile(file)
            background = this
        }
        if (autoplay) {
            start()
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)


        var widthSize = MeasureSpec.getSize(widthMeasureSpec);
        var heightSize = MeasureSpec.getSize(heightMeasureSpec);

        var usedBounds = Rive.calculateRequiredBounds(
            fit,
            alignment,
            AABB(widthSize.toFloat(), heightSize.toFloat()),
            drawable.arboardBounds()
        )

        var width: Int
        var height: Int

        //Measure Width
        when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> width = widthSize
            MeasureSpec.AT_MOST -> width = Math.min(usedBounds.width.toInt(), widthSize)
            else ->
                width = usedBounds.width.toInt()
        }

        //Measure Height
        when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> height = heightSize
            MeasureSpec.AT_MOST -> height = Math.min(usedBounds.height.toInt(), heightSize)
            else ->
                height = usedBounds.height.toInt()
        }
        setMeasuredDimension(width, height);
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
    }
}