package app.rive.runtime.kotlin

import android.animation.TimeAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.Log
import app.rive.runtime.kotlin.core.*


class RiveDrawable(
    var fit: Fit = Fit.CONTAIN,
    var alignment: Alignment = Alignment.CENTER,
    var loop: Loop = Loop.NONE,
    // TODO: would love to get rid of these three fields here.
    var artboardName: String? = null,
    var animationName: String? = null,
    var stateMachineName: String? = null,
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
    private var _playingStateMachines = HashSet<StateMachineInstance>()

    // PUBLIC
    var animations = mutableListOf<LinearAnimationInstance>()
    var stateMachines = mutableListOf<StateMachineInstance>()
    var file: File? = null
    var playingAnimations: HashSet<LinearAnimationInstance>
        get() = _playingAnimations
        private set(value) {
            _playingAnimations = value
        }
    var playingStateMachines: HashSet<StateMachineInstance>
        get() = _playingStateMachines
        private set(value) {
            _playingStateMachines = value
        }
    val isPlaying: Boolean
        get() = playingAnimations.isNotEmpty() || playingStateMachines.isNotEmpty()

    init {
        targetBounds = AABB(bounds.width().toFloat(), bounds.height().toFloat())
        animator.setTimeListener { _, _, delta ->
            advance(delta.toFloat())
        }
    }

    fun advance(delta: Float) {
        artboard?.let { ab ->
            val elapsed = delta / 1000

            // animations could change, lets cut a list.
            // order of animations is important.....
            animations.toList().forEach { animationInstance ->

                if (playingAnimations.contains(animationInstance)) {
                    val looped = animationInstance.advance(elapsed)

                    animationInstance.apply(ab, 1f)
                    if (looped == Loop.ONESHOT) {
                        _stop(animationInstance)
                    } else if (looped != null) {
                        notifyLoop(animationInstance)
                    }
                }
            }
            stateMachines.toList().forEach { stateMachineInstance ->

                if (playingStateMachines.contains(stateMachineInstance)) {
                    val stillPlaying = stateMachineInstance.advance(elapsed)

                    stateMachineInstance.apply(ab)
                    if (!stillPlaying) {
                        // State Machines need to pause not stop
                        // as they have lots of stop and go possibilities
                        _pause(stateMachineInstance)
                    }
                }
            }
            ab.advance(elapsed)
        }

        if (!isPlaying) {
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
            }
            this.artboardName = artboardName

            this.file?.let {
                setRiveFile(it)
            }
        }

    }

    fun arboardBounds(): AABB {
        var output = artboard?.bounds;
        if (output == null) {
            output = AABB(0f, 0f);
        }
        return output;
    }

    fun clear() {
        animator.cancel()
        animator.currentPlayTime = 0
        playingAnimations.clear()
        animations.clear()
        playingStateMachines.clear()
        stateMachines.clear()

    }

    fun reset() {
        Log.d("WAT", "${animations.size} ${stateMachines.size} ")
        stop()
        clear()
        file?.let {
            setRiveFile(it)
        }
        invalidateSelf()
    }

    fun destroy() {
        stop()
        renderer.cleanup()
    }

    fun play(
        animationNames: List<String>,
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO,
        areStateMachines: Boolean = false,
    ) {
        animationNames.forEach {
            _playAnimation(it, loop, direction, areStateMachines)
        }
        animator.start()
    }

    fun play(
        animationName: String,
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO,
        isStateMachine: Boolean = false,
    ) {
        _playAnimation(animationName, loop, direction, isStateMachine)
        animator.start()
    }

    fun play(
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO,
    ) {
        artboard?.let {
            if (it.animationNames.isNotEmpty()) {
                _playAnimation(it.animationNames.first(), loop, direction)
            } else if (it.stateMachineNames.isNotEmpty()) {
                _playAnimation(it.stateMachineNames.first(), loop, direction, true)
            }
        }
        animator.start()
    }

    fun pause() {
        // pause will modify playing animations, so we cut a list of it first.
        playingAnimations.toList().forEach { animation ->
            _pause(animation)
        }
        playingStateMachines.toList().forEach { stateMachine ->
            _pause(stateMachine)
        }
    }

    fun pause(animationNames: List<String>, areStateMachines: Boolean = false) {
        if (areStateMachines) {
            _stateMachines(animationNames).forEach { stateMachine ->
                _pause(stateMachine)
            }
        } else {
            _animations(animationNames).forEach { animation ->
                _pause(animation)
            }
        }
    }

    fun pause(animationName: String, isStateMachine: Boolean = false) {
        if (isStateMachine) {
            _stateMachines(animationName).forEach { stateMachine ->
                _pause(stateMachine)
            }
        } else {
            _animations(animationName).forEach { animation ->
                _pause(animation)
            }
        }

    }

    /**
     * called [stopAnimations] to avoid conflicting with [stop]
     */
    fun stopAnimations() {
        // stop will modify animations, so we cut a list of it first.
        animations.toList().forEach { animation ->
            _stop(animation)
        }
        stateMachines.toList().forEach { stateMachine ->
            _stop(stateMachine)
        }
    }

    fun stopAnimations(animationNames: List<String>, areStateMachines: Boolean = false) {
        if (areStateMachines) {
            _stateMachines(animationNames).forEach { stateMachine ->
                _stop(stateMachine)
            }
        } else {
            _animations(animationNames).forEach { animation ->
                _stop(animation)
            }
        }
    }


    fun stopAnimations(animationName: String, isStateMachine: Boolean = false) {
        if (isStateMachine) {
            _stateMachines(animationName).forEach { stateMachine ->
                _stop(stateMachine)
            }
        } else {

            _animations(animationName).forEach { animation ->
                _stop(animation)
            }
        }
    }

    fun fireState(stateMachineName: String, inputName:String){
        val stateMachineInstances = _getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMITrigger).fire()
            _play(it)
        }
        animator.start()
    }

    fun setBooleanState(stateMachineName: String, inputName:String, value:Boolean){
        val stateMachineInstances = _getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMIBoolean).value=value
            _play(it)
        }
        animator.start()
    }

    fun setNumberState(stateMachineName: String, inputName:String, value:Float){
        val stateMachineInstances = _getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMINumber).value=value
            _play(it)
        }
        animator.start()
    }

    // PRIVATE FUNCTIONS

    private fun _animations(animationName: String): List<LinearAnimationInstance> {
        return _animations(listOf(animationName))
    }

    private fun _stateMachines(animationName: String): List<StateMachineInstance> {
        return _stateMachines(listOf(animationName))
    }

    private fun _animations(animationNames: Collection<String>): List<LinearAnimationInstance> {
        return animations.filter { animationInstance ->
            animationNames.contains(animationInstance.animation.name)
        }
    }

    private fun _stateMachines(animationNames: Collection<String>): List<StateMachineInstance> {
        return stateMachines.filter { stateMachineInstance ->
            animationNames.contains(stateMachineInstance.stateMachine.name)
        }
    }

    private fun _getOrCreateStateMachines(animationName: String): List<StateMachineInstance> {
        val stateMachineInstances = _stateMachines(animationName)
        if (stateMachineInstances.isEmpty()) {
            artboard?.let {
                val stateMachine = it.stateMachine(animationName)
                val stateMachineInstance = StateMachineInstance(stateMachine)
                stateMachines.add(stateMachineInstance)
                return listOf(stateMachineInstance)
            }
        }
        return stateMachineInstances
    }

    private fun _playAnimation(
        animationName: String,
        loop: Loop = Loop.NONE,
        direction: Direction = Direction.AUTO,
        isStateMachine: Boolean = false,
    ) {
        if (isStateMachine) {
            val stateMachineInstances = _getOrCreateStateMachines(animationName)
            stateMachineInstances.forEach { stateMachineInstance ->
                _play(stateMachineInstance)
            }
        } else {
            val animationInstances = _animations(animationName)
            animationInstances.forEach { animationInstance ->
                _play(animationInstance, loop, direction)
            }
            if (animationInstances.isEmpty()) {
                artboard?.let {
                    val animation = it.animation(animationName)
                    val linearAnimation = LinearAnimationInstance(animation)
                    _play(linearAnimation, loop, direction)
                }
            }
        }

    }

    private fun _play(
        stateMachineInstance: StateMachineInstance,
    ) {
        if (!stateMachines.contains(stateMachineInstance)) {
            stateMachines.add(stateMachineInstance)
        }
        playingStateMachines.add(stateMachineInstance)
        notifyPlay(stateMachineInstance)
    }

    private fun _play(
        animationInstance: LinearAnimationInstance,
        loop: Loop,
        direction: Direction
    ) {
        // If a loop mode was specified, use it, otherwise fall back to a predefined default loop,
        // otherwise just use what the animation is configured to be.
        // not really sure if sticking loop into the xml thing makes much sense...
        val appliedLoop = if (loop == Loop.NONE) this.loop else loop
        if (appliedLoop != Loop.NONE) {
            animationInstance.animation.loop = appliedLoop
        }
        if (!animations.contains(animationInstance)) {
            if (direction == Direction.BACKWARDS) {
                animationInstance.time(animationInstance.animation.endTime)
            }
            animations.add(animationInstance)
        }
        if (direction != Direction.AUTO) {
            animationInstance.direction = direction
        }
        playingAnimations.add(animationInstance)
        notifyPlay(animationInstance)
    }

    private fun _pause(animation: LinearAnimationInstance) {
        var removed = playingAnimations.remove(animation)
        if (removed) {
            notifyPause(animation)
        }
    }

    private fun _pause(stateMachine: StateMachineInstance) {
        var removed = playingStateMachines.remove(stateMachine)
        if (removed) {
            notifyPause(stateMachine)
        }
    }


    private fun _stop(animation: LinearAnimationInstance) {
        playingAnimations.remove(animation)
        var removed = animations.remove(animation)
        if (removed) {
            notifyStop(animation)
        }
    }

    private fun _stop(stateMachine: StateMachineInstance) {
        playingStateMachines.remove(stateMachine)
        var removed = stateMachines.remove(stateMachine)
        if (removed) {
            notifyStop(stateMachine)
        }
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
                stateMachineName?.let {
                    play(animationName = it, isStateMachine = true)
                } ?: run {
                    play()
                }

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
        fun notifyPlay(animation: PlayableInstance)
        fun notifyPause(animation: PlayableInstance)
        fun notifyStop(animation: PlayableInstance)
        fun notifyLoop(animation: PlayableInstance)
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

    private fun notifyPlay(playableInstance: PlayableInstance) {
        listeners.toList().forEach {
            it.notifyPlay(playableInstance)
        }
    }

    private fun notifyPause(playableInstance: PlayableInstance) {
        listeners.toList().forEach {
            it.notifyPause(playableInstance)
        }
    }

    private fun notifyStop(playableInstance: PlayableInstance) {
        listeners.toList().forEach {
            it.notifyStop(playableInstance)
        }
    }

    private fun notifyLoop(playableInstance: PlayableInstance) {
        listeners.toList().forEach {
            it.notifyLoop(playableInstance)
        }
    }
}