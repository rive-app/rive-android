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
 * - Configure [loop mode][R.styleable.RiveAnimationView_riveLoop] to configure if animations should loop, play once, or pingpong back and forth. Defaults to the setup in the rive file.
 */
class RiveAnimationView(context: Context, attrs: AttributeSet? = null) : View(context, attrs),
    Observable<RiveDrawable.Listener> {
    // There's always just one drawable associated with an animation view
    val drawable = RiveDrawable()

    private var resourceId: Int? = null

    var fit: Fit
        get() = drawable.fit
        set(value) {
            drawable.fit = value
        }

    var alignment: Alignment
        get() = drawable.alignment
        set(value) {
            drawable.alignment = value
        }

    /**
     * Getter for the loaded [Rive file][File].
     */
    val file: File?
        get() = drawable.file

    /**
     * Getter/Setter for the currently loaded artboard Name
     * Setting a new name, will load the new artboard & depending on [autoplay] play them
     */
    var artboardName: String?
        get() = drawable.artboardName
        set(name) {
            drawable.setArtboardByName(name)
        }

    /**
     * Getter/Setter for [autoplay].
     */
    var autoplay: Boolean
        get() = drawable.autoplay
        set(value) {
            drawable.autoplay = value
        }

    /**
     * Get the currently loaded [animation instances][LinearAnimationInstance].
     */
    val animations: List<LinearAnimationInstance>
        get() = drawable.animations

    /**
     * Get the currently loaded [state machine instances][StateMachineInstance].
     */
    val stateMachines: List<StateMachineInstance>
        get() = drawable.stateMachines

    /**
     * Get the currently playing [animation instances][LinearAnimationInstance].
     */
    val playingAnimations: HashSet<LinearAnimationInstance>
        get() = drawable.playingAnimations

    /**
     * Get the currently playing [state machine instances][StateMachineInstance].
     */
    val playingStateMachines: HashSet<StateMachineInstance>
        get() = drawable.playingStateMachines

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.RiveAnimationView,
            0, 0
        ).apply {
            try {
                val alignmentIndex = getInteger(R.styleable.RiveAnimationView_riveAlignment, 4)
                val fitIndex = getInteger(R.styleable.RiveAnimationView_riveFit, 1)
                val loopIndex = getInteger(R.styleable.RiveAnimationView_riveLoop, 3)
                val autoplay = getBoolean(R.styleable.RiveAnimationView_riveAutoPlay, autoplay)
                val artboardName = getString(R.styleable.RiveAnimationView_riveArtboard)
                val animationName = getString(R.styleable.RiveAnimationView_riveAnimation)
                val stateMachineName = getString(R.styleable.RiveAnimationView_riveStateMachine)
                val resourceId = getResourceId(R.styleable.RiveAnimationView_riveResource, -1)

                if (resourceId != -1) {
                    setRiveResource(
                        resourceId,
                        alignment = Alignment.values()[alignmentIndex],
                        fit = Fit.values()[fitIndex],
                        loop = Loop.values()[loopIndex],
                        autoplay = autoplay,
                        artboardName = artboardName,
                        stateMachineName = stateMachineName,
                        animationName = animationName,
                    )
                } else {
                    drawable.alignment = Alignment.values()[alignmentIndex]
                    drawable.fit = Fit.values()[fitIndex]
                    drawable.loop = Loop.values()[loopIndex]
                    drawable.autoplay = autoplay
                    drawable.artboardName = artboardName
                    drawable.animationName = animationName
                    drawable.stateMachineName = stateMachineName
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
    fun pause(animationNames: List<String>, areStateMachines: Boolean = false) {
        drawable.pause(animationNames, areStateMachines)
    }


    /**
     * Pauses any [animation instances][LinearAnimationInstance] for an [animation][Animation]
     * called [animationName].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animation][Animation]
     */
    fun pause(animationName: String, isStateMachine: Boolean = false) {
        drawable.pause(animationName, isStateMachine)
    }

    /**
     * Stops all [animation instances][LinearAnimationInstance].
     *
     * Animations Instances will be disposed of completely.
     * Subsequent plays will create new [animation instances][LinearAnimationInstance]
     * for the [animations][Animation] in the file.
     */
    fun stop() {
        drawable.stopAnimations()
    }

    /**
     * Stops any [animation instances][LinearAnimationInstance] for [animations][Animation] with
     * any of the provided [names][animationNames].
     *
     * Animations Instances will be disposed of completely.
     * Subsequent plays will create new [animation instances][LinearAnimationInstance]
     * for the [animations][Animation] in the file.
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animation][Animation]
     */
    fun stop(animationNames: List<String>, areStateMachines: Boolean = false) {
        drawable.stopAnimations(animationNames, areStateMachines)
    }

    /**
     * Stops any [animation instances][LinearAnimationInstance] for an [animation][Animation]
     * called [animationName].
     *
     * Animations Instances will be disposed of completely.
     * Subsequent plays will create new [animation instances][LinearAnimationInstance]
     * for the [animations][Animation] in the file.
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animation][Animation]
     */
    fun stop(animationName: String, isStateMachine: Boolean = false) {
        drawable.stopAnimations(animationName, isStateMachine)
    }

    /**
     * Plays the first [animations][Animation] found for a [File].
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
        direction: Direction = Direction.AUTO,
        areStateMachines: Boolean = false
    ) {
        drawable.play(animationNames, loop, direction, areStateMachines)
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
        direction: Direction = Direction.AUTO, isStateMachine: Boolean = false
    ) {
        drawable.play(animationName, loop, direction, isStateMachine)
    }

    /**
     * Completely reset the view, this will also reload the [resourceId] if one was provided.
     *
     * Resetting allows you to go back to the initial state of the artboard, before any animations
     * were applied and will attempt to setup the same conditions as are set for the currently loaded animations.
     *
     * If you want to change this selection [setRiveResource], or [setRiveFile] will offer more options.
     *
     * Some rive users will want to create 'idle' or 'reset' animations in the rive editor to get
     * the file back to a neutral position without having to reload the rive file
     */
    fun reset() {
        resourceId?.let {
            setRiveResource(
                it,
                fit = drawable.fit,
                alignment = drawable.alignment,
                loop = drawable.loop,
                artboardName = drawable.artboardName,
                animationName = drawable.animationName,
                stateMachineName = drawable.stateMachineName,
                autoplay = drawable.autoplay
            )
        } ?: run {
            drawable.reset()
        }
    }

    /**
     * Fire the [SMITrigger] input called [inputName] on all active matching state machines
     */
    fun fireState(stateMachineName: String, inputName:String){
        drawable.fireState(stateMachineName, inputName)
    }

    /**
     * Update the state of the [SMIBoolean] input called [inputName] on all active matching state machines
     * to [value]
     */
    fun setBooleanState(stateMachineName: String, inputName:String, value:Boolean){
        drawable.setBooleanState(stateMachineName, inputName, value)
    }

    /**
     * Update the state of the [SMINumber] input called [inputName] on all active matching state machines
     * to [value]
     */
    fun setNumberState(stateMachineName: String, inputName:String, value:Float){
        drawable.setNumberState(stateMachineName, inputName, value)
    }

    /**
     * Check if the animation is currently playing
     */
    val isPlaying: Boolean
        get() = drawable.isPlaying

    /**
     * Load the [resource Id][resId] as a rive file and load it into the view.
     *
     * - Optionally provide an [artboardName] to use, or the first artboard in the file.
     * - Optionally provide an [animationName] to load by default, playing without any suggested animations names will simply play the first animaiton
     * - Enable [autoplay] to start the animation without further prompts.
     * - Configure [alignment] to specify how the animation should be aligned to its container.
     * - Configure [fit] to specify how and if the animation should be resized to fit its container.
     * - Configure [loop] to configure if animations should loop, play once, or pingpong back and forth. Defaults to the setup in the rive file.
     *
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the file.
     */
    fun setRiveResource(
        @RawRes resId: Int,
        artboardName: String? = null,
        animationName: String? = null,
        stateMachineName: String? = null,
        autoplay: Boolean = drawable.autoplay,
        fit: Fit = Fit.CONTAIN,
        alignment: Alignment = Alignment.CENTER,
        loop: Loop = Loop.NONE,
    ) {
        resourceId = resId
        val bytes = resources.openRawResource(resId).readBytes()
        setRiveBytes(
            bytes,
            fit = fit,
            alignment = alignment,
            loop = loop,
            artboardName = artboardName,
            animationName = animationName,
            stateMachineName = stateMachineName,
            autoplay = autoplay
        )
    }

    /**
     * Create a view file from a byte array and load it into the view
     *
     * - Optionally provide an [artboardName] to use, or the first artboard in the file.
     * - Optionally provide an [animationName] to load by default, playing without any suggested animations names will simply play the first animaiton
     * - Enable [autoplay] to start the animation without further prompts.
     * - Configure [alignment] to specify how the animation should be aligned to its container.
     * - Configure [fit] to specify how and if the animation should be resized to fit its container.
     * - Configure [loop] to configure if animations should loop, play once, or pingpong back and forth. Defaults to the setup in the rive file.
     *
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the file.
     */
    fun setRiveBytes(
        bytes: ByteArray,
        artboardName: String? = null,
        animationName: String? = null,
        stateMachineName: String? = null,
        autoplay: Boolean = drawable.autoplay,
        fit: Fit = Fit.CONTAIN,
        alignment: Alignment = Alignment.CENTER,
        loop: Loop = Loop.NONE,
    ) {
        val file = File(bytes)
        setRiveFile(
            file,
            fit = fit,
            alignment = alignment,
            loop = loop,
            artboardName = artboardName,
            animationName = animationName,
            stateMachineName = stateMachineName,
            autoplay = autoplay
        )
    }

    /**
     * Load the [rive file][File] into the view.
     *
     * - Optionally provide an [artboardName] to use, this defaults to the first artboard in the file.
     * - Optionally provide an [animationName] to load by default, playing without any suggested animations names will simply play the first animations.
     * - Enable [autoplay] to start the animation without further prompts.
     * - Configure [alignment] to specify how the animation should be aligned to its container.
     * - Configure [fit] to specify how and if the animation should be resized to fit its container.
     * - Configure [loop] to configure if animations should loop, play once, or pingpong back and forth. Defaults to the setup in the rive file.
     *
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the file.
     */
    private fun setRiveFile(
        file: File,
        artboardName: String?,
        animationName: String?,
        stateMachineName: String?,
        autoplay: Boolean,
        fit: Fit,
        alignment: Alignment,
        loop: Loop,
    ) {
        drawable.stop()
        drawable.clear()
        drawable.fit = fit
        drawable.alignment = alignment
        drawable.loop = loop
        drawable.autoplay = autoplay
        drawable.animationName = animationName
        drawable.stateMachineName = stateMachineName
        drawable.artboardName = artboardName

        drawable.setRiveFile(file)
        background = drawable

        requestLayout()
    }


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
            MeasureSpec.EXACTLY -> height = providedHeight
            MeasureSpec.AT_MOST -> height = Math.min(usedBounds.height.toInt(), providedHeight)
            else ->
                height = usedBounds.height.toInt()
        }
        setMeasuredDimension(width, height);
    }

    override fun registerListener(listener: RiveDrawable.Listener) {
        drawable.registerListener(listener)
    }

    override fun unregisterListener(listener: RiveDrawable.Listener) {
        drawable.unregisterListener(listener)
    }

    fun destroy() {
        drawable.destroy()
    }

}