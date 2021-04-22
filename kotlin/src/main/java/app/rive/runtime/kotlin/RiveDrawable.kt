package app.rive.runtime.kotlin

import android.animation.TimeAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import app.rive.runtime.kotlin.core.*


class RiveDrawable(
    var fit: Fit = Fit.CONTAIN,
    var alignment: Alignment = Alignment.CENTER,
    var loop: Loop = Loop.NONE,
    var artboardName: String? = null,
    var animationName: String? = null,
    var autoplay: Boolean = true
) : Drawable(), Animatable, Observable<RiveDrawable.Listener> {
    // PRIVATE
    private val renderer = Renderer()
    private val animator = TimeAnimator()
    private var listeners = HashSet<RiveDrawable.Listener>()
    private var targetBounds: AABB
    private var artboard: Artboard? = null
    private var boundsCache: Rect? = null
    private var _playingAnimations = HashSet<LinearAnimationInstance>()

    // PUBLIC
    var animations = mutableListOf<LinearAnimationInstance>()
    var file: File? = null
    var playingAnimations: HashSet<LinearAnimationInstance>
        get() = _playingAnimations
        private set(value) {
            _playingAnimations = value
        }
    val isPlaying: Boolean
        get() = playingAnimations.isNotEmpty()

    init {
        targetBounds = AABB(bounds.width().toFloat(), bounds.height().toFloat())
        animator.setTimeListener { _, _, delta ->
            advance(delta.toFloat())
        }
    }

    fun advance(delta: Float) {
        artboard?.let { ab ->
            val elapsed = delta / 1000

            animations.forEach {
                // order of animations is important.
                if (playingAnimations.contains(it)) {
                    // TODO: this gives us a loop mode if the animation hit the end/ looped.
                    // TODO: we should probably think of a clearer way of doing this.
                    val looped = it.advance(elapsed)
                    it.apply(ab, 1f)
                    if (looped == Loop.ONESHOT) {
                        // we're done. with our oneshot. might regret resetting time?
                        if (it.direction == Direction.BACKWARDS) {
                            it.time(it.animation.workEndTime)
                        } else {
                            it.time(it.animation.workStartTime)
                        }


                    }
                }
            }
            ab.advance(elapsed)

        }
        // TODO: set continuePlaying to false if all animations have come to an end.
        if (playingAnimations.isEmpty()) {
            animator.pause()
        }
        invalidateSelf()
    }

    // PUBLIC FUNCTIONS
    fun setRiveFile(file: File) {
        this.file = file
        selectArtboard()
    }

    fun setArtboardByName(artboardName: String?) {
        stop()
        if (file == null) {
            this.artboardName = artboardName
        } else {
            file?.let {
                if (!it.artboardNames.contains(artboardName)) {
                    throw RiveException("Artboard $artboardName not found")
                }
                this.artboardName = artboardName
                selectArtboard()
            }
        }

        this.file?.let {
            setRiveFile(it)
        }
    }

    fun arboardBounds(): AABB {
        var output = artboard?.bounds;
        if (output == null) {
            output = AABB(0f, 0f);
        }
        return output;
    }

    fun reset() {
        animator.cancel()
        animator.currentPlayTime = 0
        playingAnimations.clear()
        animations.clear()
        file?.let {
            setRiveFile(it)
        }
        invalidateSelf()
    }

    fun destroy() {
        renderer.cleanup()
    }

    fun play(
        animationNames: List<String>,
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO,
    ) {
        animationNames.forEach {
            _playAnimation(it, loop, direction)
        }
        animator.start()
    }

    fun play(
        animationName: String,
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO,
    ) {
        _playAnimation(animationName, loop, direction)
        animator.start()
    }

    fun play(
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO,
    ) {

        _playAllAnimations(loop, direction)
        animator.start()
    }

    fun pause() {
        // pause will modify playing animations, so we cut a list of it first.
        playingAnimations.toList().forEach { animation ->
            _pause(animation)
        }
    }

    fun pause(animationNames: List<String>) {
        _animations(animationNames).forEach { animation ->
            _pause(animation)
        }
    }

    fun pause(animationName: String) {
        _animations(animationName).forEach { animation ->
            _pause(animation)
        }
    }

    /**
     * called [stopAnimations] to avoid conflicting with [stop]
     */
    fun stopAnimations() {
        playingAnimations.clear()
        animations.clear()
    }

    fun stopAnimations(animationNames: List<String>) {
        animationNames.forEach { animationName ->
            stopAnimations(animationName)
        }
    }

    fun stopAnimations(animationName: String) {
        var animations = _animations(animationName)
        _stop(animations)
    }

    // PRIVATE FUNCTIONS

    private fun _animations(animationName: String): List<LinearAnimationInstance> {
        return _animations(listOf(animationName))
    }

    private fun _animations(animationNames: Collection<String>): List<LinearAnimationInstance> {
        return animations.filter { animationInstance ->
            animationNames.contains(animationInstance.animation.name)
        }
    }

    private fun _playAnimation(
        animationName: String,
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO
    ) {
        // If a loop mode was specified, use it, otherwise fall back to a predefined default loop,
        // otherwise just use what the animation is configured to be.
        val appliedLoop = if (loop == Loop.NONE) this.loop else loop
        val foundAnimationInstance = animations.find { it.animation.name == animationName }
        if (foundAnimationInstance == null) {
            artboard?.let {
                val animation = it.animation(animationName)
                if (appliedLoop != Loop.NONE) {
                    animation.loop = appliedLoop
                }
                _addAnimation(animation, direction)
            }
        } else {
            if (appliedLoop != Loop.NONE) {
                foundAnimationInstance.animation.loop = appliedLoop
            }
            if (direction != Direction.AUTO) {
                foundAnimationInstance.direction = direction
            }
            playingAnimations.add(foundAnimationInstance)
        }
    }

    private fun _playAllAnimations(loop: Loop = Loop.NONE, direction: Direction = Direction.AUTO) {
        artboard?.let {
            for (i in 0 until it.animationCount) {
                _playAnimation(it.animation(i).name, loop, direction)
            }
        }
    }

    private fun _pause(animation: LinearAnimationInstance) {
        playingAnimations.remove(animation)
    }

    private fun _stop(_animations: List<LinearAnimationInstance>) {
        playingAnimations.removeAll(_animations)
        animations.removeAll(_animations)
    }

    private fun _addAnimation(animation: Animation, direction: Direction) {
        var linearAnimation = LinearAnimationInstance(animation)
        if (direction != Direction.AUTO) {
            linearAnimation.direction = direction
            if (direction == Direction.BACKWARDS) {
                linearAnimation.time(animation.workEndTime)
            }
        }
        animations.add(linearAnimation)
        playingAnimations.add(linearAnimation)

    }

    private fun selectArtboard() {
        file?.let { file ->
            artboardName?.let {
                setArtboard(file.artboard(it))
            } ?: run {
                setArtboard(file.firstArtboard)
            }
        }
    }

    private fun setArtboard(artboard: Artboard) {
        this.artboard = artboard
        if (autoplay) {
            animationName?.let {
                play(animationName = it)
            } ?: run {
                play()
            }

        } else {
            artboard.advance(0f)
        }
    }

    /*
    DRAWABLE OVERRIDES
     */

    override fun onBoundsChange(bounds: Rect?) {
        super.onBoundsChange(bounds)

        bounds?.let {
            targetBounds = AABB(bounds.width().toFloat(), bounds.height().toFloat())
        }
    }

    override fun draw(canvas: Canvas) {
        artboard?.let { ab ->

            if (boundsCache != bounds) {
                boundsCache = bounds
                targetBounds = AABB(bounds.width().toFloat(), bounds.height().toFloat())
            }
            renderer.canvas = canvas
            renderer.align(fit, alignment, targetBounds, ab.bounds)
            val saved = canvas.save()
            ab.draw(renderer)
            canvas.restoreToCount(saved)
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return artboard?.bounds?.width?.toInt() ?: -1
    }

    override fun getIntrinsicHeight(): Int {
        return artboard?.bounds?.height?.toInt() ?: -1
    }

    /*
    ANIMATOR OVERRIDES
     */

    override fun start() {
        animator.start()
    }

    override fun stop() {
        stopAnimations()
        animator.cancel()
    }

    override fun isRunning(): Boolean {
        return isPlaying
    }

    /*
   LISTENER INTERFACE
     */
    interface Listener {
        fun notifyPlay(animation: LinearAnimationInstance)
        fun notifyPause(animation: LinearAnimationInstance)
        fun notifyStop(animation: LinearAnimationInstance)
        fun notifyLoop(animation: LinearAnimationInstance)
    }

    /*
    LISTENER OVERRIDES
     */
    override fun registerListener(listener: RiveDrawable.Listener) {
        listeners.add(listener)
    }

    override fun unregisterListener(listener: RiveDrawable.Listener) {
        listeners.remove(listener)
    }
}