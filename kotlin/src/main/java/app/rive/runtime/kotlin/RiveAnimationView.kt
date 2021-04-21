package app.rive.runtime.kotlin

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RawRes
import app.rive.runtime.kotlin.core.*
import java.util.*

/**
 * This view aims to provide the most straightforward way to get rive animations into your application.
 *
 * Simply add the view to your activities, and you are be good to to!
 *
 * Very simple animations can be configured completely from a layout file. We also also expose a
 * thin api layer to allow more control over how animations are playing.
 *
 * All of this is built upon the core [rive animation wrappers][app.rive.runtime.kotlin.core],
 * which are designed to expose our c++ rive animation runtimes directly, and can be used
 * directly for the most flexibility.
 *
 *
 * Xml [attrs] can be used to set initial values for many
 * - Provide the [resource][R.styleable.RiveAnimationView_riveResource] to load as a rive file, this can be done later with [setRiveResource] or [setRiveFile].
 * - Determine the [artboard][R.styleable.RiveAnimationView_riveArtboard] to use, this defaults to the first artboard in the file.
 * - Enable or disable [autoplay][R.styleable.RiveAnimationView_riveAutoPlay] to start the animation as soon as its available, or leave it to false to control its playback later. defaults to enabled.
 * - Configure [alignment][R.styleable.RiveAnimationView_riveAlignment] to specify how the animation should be aligned to its container.
 * - Configure [fit][R.styleable.RiveAnimationView_riveFit] to specify how and if the animation should be resized to fit its container.
 * - Configure [loop mode][R.styleable.RiveAnimationView_riveLoop] to configure if animations should loop, play once, or pingpong back and forth.
 */
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
                val autoplay = getBoolean(R.styleable.RiveAnimationView_riveAutoPlay, autoplay)
                val artboardName = getString(R.styleable.RiveAnimationView_riveArtboard)
                val animationName = getString(R.styleable.RiveAnimationView_riveAnimation)
                val resourceId = getResourceId(R.styleable.RiveAnimationView_riveResource, -1)

                drawable.alignment = Alignment.values()[alignmentIndex]
                drawable.fit = Fit.values()[fitIndex]
                drawable.loop = Loop.values()[loopIndex]
                drawable.autoplay = autoplay
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

    /**
     * Pauses all playing [animation instance][LinearAnimationInstance].
     */
    fun pause() {
        drawable.pause()
    }

    /**
     * Pauses any [animation instances][LinearAnimationInstance] for [animations][Animation] with
     * any of the provided [names][animationNames].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animation][Animation]
     */
    fun pause(animationNames: List<String>) {
        drawable.pause(animationNames)
    }

    /**
     * Pauses any [animation instances][LinearAnimationInstance] for an [animation][Animation]
     * called [animationName].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animation][Animation]
     */
    fun pause(animationName: String) {
        drawable.pause(animationName)
    }

    /**
     * Plays all found [animations][Animation] for a [File].
     *
     * @experimental Optionally provide a [loop mode][Loop] to overwrite the animations configured loop mode.
     * Already playing animation instances will be updated to this loop mode if provided.
     *
     * @experimental Optionally provide a [direction][Direction] to set the direction an animation is playing in.
     * Already playing animation instances will be updated to this direction immediately.
     * Backwards animations will start from the end.
     *
     * For [animations][Animation] without an [animation instance][LinearAnimationInstance] one will be created and played.
     */
    fun play(
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO
    ) {
        drawable.play(loop, direction)
    }

    /**
     * Plays any [animation instances][LinearAnimationInstance] for [animations][Animation] with
     * any of the provided [names][animationNames].
     *
     * see [play] for more details on options
     */
    fun play(
        animationNames: List<String>,
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO
    ) {
        drawable.play(animationNames, loop, direction)
    }

    /**
     * Plays any [animation instances][LinearAnimationInstance] for an [animation][Animation]
     * called [animationName].
     *
     * see [play] for more details on options
     */
    fun play(
        animationName: String,
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO
    ) {
        drawable.play(animationName, loop, direction)
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

    val file: File?
        get() = drawable.file

    var artboardName: String?
        get() = drawable.artboardName
        set(name) {
            drawable.setArtboardByName(name)
        }

    var autoplay: Boolean
        get() = drawable.autoplay
        set(value) {
            drawable.autoplay = value
        }

    val animations: List<LinearAnimationInstance>
        get() = drawable.animations

    val playingAnimations: HashSet<LinearAnimationInstance>
        get() = drawable.playingAnimations

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
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

        setMeasuredDimension(width, height);
    }
}