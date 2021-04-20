package app.rive.runtime.kotlin

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RawRes
import app.rive.runtime.kotlin.core.*

class RiveAnimationView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var drawable: RiveDrawable = RiveDrawable();
    private var resourceId: Int? = null;

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.RiveAnimationView,
            0, 0
        ).apply {
            try {
                val alignmentIndex = getInteger(R.styleable.RiveAnimationView_riveAlignment, 4)
                val fitIndex = getInteger(R.styleable.RiveAnimationView_riveFit, 1)
                val loopIndex = getInteger(R.styleable.RiveAnimationView_riveLoop, 1)
                val autoplay = getBoolean(R.styleable.RiveAnimationView_riveAutoPlay, true)
                val artboardName = getString(R.styleable.RiveAnimationView_riveArtboard)
                val animationName = getString(R.styleable.RiveAnimationView_riveAnimation)
                val resourceId = getResourceId(R.styleable.RiveAnimationView_riveResource, -1)

                drawable.alignment = Alignment.values()[alignmentIndex]
                drawable.fit = Fit.values()[fitIndex]
                drawable.loop = Loop.values()[loopIndex]
                drawable.autoplay =autoplay
                drawable.artboardName = artboardName
                drawable.animationName = animationName

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

    fun pause(animationNames: List<String>) {
        drawable.pause(animationNames)
    }

    fun pause(animationName: String) {
        drawable.pause(animationName)
    }

    fun play(
        loop: Loop = Loop.NONE
    ) {
        drawable.play(loop)
    }

    fun play(
        animationNames: List<String>,
        loop: Loop = Loop.NONE
    ) {
        drawable.play(animationNames, loop)
    }

    fun play(
        animationName: String,
        loop: Loop = Loop.NONE
    ) {
        drawable.play(animationName, loop)
    }

    fun direction(
        direction: Direction
    ) {
        drawable.setDirection(direction)
    }


    fun reset() {
        drawable.reset()
        resourceId?.let {
            setRiveResource(it)
        }
    }

    val isPlaying: Boolean
        get() = drawable.isPlaying

    fun setRiveResource(@RawRes resId: Int) {
        resourceId = resId
        val file = File(resources.openRawResource(resId).readBytes())
        setRiveFile(file)

    }

    fun setRiveFile(file: File) {
        drawable.run {
            reset()

        }

        // TODO: we maybe not be cleaning something up here,
        //       as we shouldnt have create a new drawable
        drawable = RiveDrawable(
            fit = drawable.fit,
            alignment = drawable.alignment,
            loop = drawable.loop,
            autoplay = drawable.autoplay,
            animationName = drawable.animationName,
            artboardName = drawable.artboardName,
        )

        drawable.setRiveFile(file)
        background = drawable

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
            drawable.fit,
            drawable.alignment,
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

    val file: File?
        get() = drawable.file

    var artboardName: String?
        get() = drawable.artboardName
        set(name) {
            drawable.setArtboardByName(name)
        }

    val animations: List<LinearAnimationInstance>
        get() = drawable.animations

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
    }
}