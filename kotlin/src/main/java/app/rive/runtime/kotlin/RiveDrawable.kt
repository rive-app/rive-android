package app.rive.runtime.kotlin

import android.animation.TimeAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import app.rive.runtime.kotlin.core.*
import app.rive.runtime.kotlin.core.errors.ArtboardException


class RiveDrawable(
    fit: Fit = Fit.CONTAIN,
    alignment: Alignment = Alignment.CENTER,
    var loop: Loop = Loop.AUTO,
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
    private var selectedArtboard: Artboard? = null
    private var _activeArtboard: Artboard? = null
    private var boundsCache: Rect? = null
    private var _playingAnimations = HashSet<LinearAnimationInstance>()
    private var _playingStateMachines = HashSet<StateMachineInstance>()

    // PUBLIC
    var fit: Fit = fit
        get() = field
        set(value) {
            field = value
            invalidateSelf()
        }
    var alignment: Alignment = alignment
        get() = field
        set(value) {
            field = value
            invalidateSelf()
        }
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

    val activeArtboard: Artboard?
        get() = _activeArtboard

    init {
        targetBounds = AABB(bounds.width().toFloat(), bounds.height().toFloat())
        animator.setTimeListener { _, _, delta ->
            advance(delta.toFloat())
        }
    }

    fun advance(delta: Float) {
        _activeArtboard?.let { ab ->
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
                    val stillPlaying = stateMachineInstance.advance(ab, elapsed)

                    stateMachineInstance.statesChanged.forEach {
                        notifyStateChanged(stateMachineInstance, it)
                    }
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
                    throw ArtboardException("Artboard $artboardName not found")
                }
            }
            this.artboardName = artboardName
            selectArtboard()
        }

    }

    fun arboardBounds(): AABB {
        var output = _activeArtboard?.bounds
        if (output == null) {
            output = AABB(0f, 0f)
        }
        return output
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
        stop()
        clear()
        selectedArtboard?.let {
            setArtboard(it)
        }
        invalidateSelf()
    }

    fun finalize() {
        renderer.cleanup()
    }

    fun play(
        animationNames: List<String>,
        loop: Loop = Loop.AUTO,
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
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        isStateMachine: Boolean = false,
    ) {
        _playAnimation(animationName, loop, direction, isStateMachine)
        animator.start()
    }

    fun play(
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
    ) {
        _activeArtboard?.let {
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

    fun fireState(stateMachineName: String, inputName: String) {
        val stateMachineInstances = _getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMITrigger).fire()
            _play(it)
        }
        animator.start()
    }

    fun setBooleanState(stateMachineName: String, inputName: String, value: Boolean) {
        val stateMachineInstances = _getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMIBoolean).value = value
            _play(it)
        }
        animator.start()
    }

    fun setNumberState(stateMachineName: String, inputName: String, value: Float) {
        val stateMachineInstances = _getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMINumber).value = value
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
            _activeArtboard?.let {
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
        loop: Loop = Loop.AUTO,
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
                _activeArtboard?.let {
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
        val appliedLoop = if (loop == Loop.AUTO) this.loop else loop
        if (appliedLoop != Loop.AUTO) {
            animationInstance.loop = appliedLoop
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
        val removed = playingAnimations.remove(animation)
        if (removed) {
            notifyPause(animation)
        }
    }

    private fun _pause(stateMachine: StateMachineInstance) {
        val removed = playingStateMachines.remove(stateMachine)
        if (removed) {
            notifyPause(stateMachine)
        }
    }


    private fun _stop(animation: LinearAnimationInstance) {
        playingAnimations.remove(animation)
        val removed = animations.remove(animation)
        if (removed) {
            notifyStop(animation)
        }
    }

    private fun _stop(stateMachine: StateMachineInstance) {
        playingStateMachines.remove(stateMachine)
        val removed = stateMachines.remove(stateMachine)
        if (removed) {
            notifyStop(stateMachine)
        }
    }

    private fun selectArtboard() {
        file?.let { file ->
            artboardName?.let {
                selectedArtboard = file.artboard(it)
                selectedArtboard?.let {
                    setArtboard(it)
                }

            } ?: run {
                selectedArtboard = file.firstArtboard
                selectedArtboard?.let {
                    setArtboard(it)
                }
            }
        }
    }

    private fun setArtboard(artboard: Artboard) {
        this._activeArtboard = artboard.getInstance()

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
            this._activeArtboard?.advance(0f)
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
        _activeArtboard?.let { ab ->
            if (boundsCache != bounds) {
                boundsCache = bounds
                targetBounds = AABB(bounds.width().toFloat(), bounds.height().toFloat())
            }
            renderer.align(fit, alignment, targetBounds, ab.bounds)
            renderer.draw(ab, canvas)
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return _activeArtboard?.bounds?.width?.toInt() ?: -1
    }

    override fun getIntrinsicHeight(): Int {
        return _activeArtboard?.bounds?.height?.toInt() ?: -1
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
        fun notifyStateChanged(stateMachineName: String, stateName: String)
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

    private fun notifyStateChanged(stateMachine: StateMachineInstance, state: LayerState) {
        listeners.toList().forEach {
            it.notifyStateChanged(stateMachine.stateMachine.name, state.toString())
        }
    }
}