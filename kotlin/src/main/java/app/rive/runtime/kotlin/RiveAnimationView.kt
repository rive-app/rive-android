package app.rive.runtime.kotlin

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.RawRes
import app.rive.runtime.kotlin.core.*

class RiveAnimationView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var animationName: String? = null;
    private var artboardName: String? = null;
    private var autoplay: Boolean = true;
    private var fit: Fit = Fit.CONTAIN
    private var loop: Loop = Loop.LOOP
    private var alignment: Alignment = Alignment.CENTER
    private var drawable: RiveDrawable = RiveDrawable(fit, alignment, loop);

    init {
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

                artboardName = getString(R.styleable.RiveAnimationView_riveArtboard)
                animationName = getString(R.styleable.RiveAnimationView_riveAnimation)

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
        drawable = RiveDrawable(fit, alignment, loop, artboardName, animationName).apply {
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

        val widthMode = MeasureSpec.getMode(widthMeasureSpec);
        val heightMode = MeasureSpec.getMode(heightMeasureSpec);

        var providedWidth = MeasureSpec.getSize(widthMeasureSpec);
        var providedHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode == MeasureSpec.UNSPECIFIED) {
            // Width is set to "whatever" we should ask our artboard?
            providedWidth = drawable.intrinsicWidth
        }
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            // Height is set to "whatever" we should ask our artboard?
            providedHeight = drawable.intrinsicHeight
        }

        // Lets work out how much space our artboard is going to actually use.
        var usedBounds = Rive.calculateRequiredBounds(
            fit,
            alignment,
            AABB(providedWidth.toFloat(), providedHeight.toFloat()),
            drawable.arboardBounds()
        )

        var width: Int
        var height: Int

        //Measure Width
        when (widthMode) {
            MeasureSpec.EXACTLY -> width = providedWidth
            MeasureSpec.AT_MOST -> width = Math.min(usedBounds.width.toInt(), providedWidth)
            else ->
                width = usedBounds.width.toInt()
        }
        MeasureSpec.UNSPECIFIED

        when (heightMode) {
            MeasureSpec.EXACTLY -> height = providedWidth
            MeasureSpec.AT_MOST -> height = Math.min(usedBounds.height.toInt(), providedWidth)
            else ->
                height = usedBounds.height.toInt()
        }
        // Leaving this here as this is probably going to need some tweaking as we figure out how this works.
        //        Log.d("$artboard", "Width exactly: ${widthMode==MeasureSpec.EXACTLY} at_most: ${heightMode==MeasureSpec.AT_MOST}")
        //        Log.d("$artboard", "Height exactly: ${MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.EXACTLY} at_most: ${MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.AT_MOST}")
        //        Log.d("$artboard", "Provided Width: $providedWidth Height:$providedHeight")
        //        Log.d("$artboard", "Artboard Used Width: ${usedBounds.width.toInt()} Height:${usedBounds.height.toInt()}")
        //
        //        Log.d("$artboard", "Selected Width: $width Height:$height")
        setMeasuredDimension(width, height);
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
    }
}