package app.rive.runtime.kotlin

import app.rive.runtime.kotlin.core.*
import app.rive.runtime.kotlin.core.errors.ArtboardException
import app.rive.runtime.kotlin.renderers.RendererSkia
import java.util.*
import kotlin.collections.HashSet

open class RiveArtboardRenderer(
    // PUBLIC
    fit: Fit = Fit.CONTAIN,
    alignment: Alignment = Alignment.CENTER,
    var loop: Loop = Loop.AUTO,
    // TODO: would love to get rid of these three fields here.
    var artboardName: String? = null,
    var animationName: String? = null,
    var stateMachineName: String? = null,
    var autoplay: Boolean = true,
    trace: Boolean = false
) : Observable<RiveArtboardRenderer.Listener>,
    RendererSkia(trace) {
    // PRIVATE
    private var listeners = HashSet<RiveArtboardRenderer.Listener>()
    var targetBounds: AABB = AABB(0f, 0f)
    private var selectedArtboard: Artboard? = null
    var activeArtboard: Artboard? = null
        private set

    // warning: toHashSet access is not thread-safe, use playingAnimations instead
    private var playingAnimationSet =
        Collections.synchronizedSet(HashSet<LinearAnimationInstance>())
    val playingAnimations: HashSet<LinearAnimationInstance>
        public get() {
            return synchronized(playingAnimationSet) {
                playingAnimationSet.toHashSet()
            }
        }

    // warning: toHashSet access is not thread-safe, use playingStateMachines instead
    private var playingStateMachineSet =
        Collections.synchronizedSet(HashSet<StateMachineInstance>())
    val playingStateMachines: HashSet<StateMachineInstance>
        public get() {
            // toHashSet is not thread safe...
            return synchronized(playingStateMachineSet) {
                playingStateMachineSet.toHashSet()
            }
        }

    // warning: tolist access is not thread-safe, use animations instead
    private var animationList =
        Collections.synchronizedList(mutableListOf<LinearAnimationInstance>())
    val animations: List<LinearAnimationInstance>
        public get() {
            return synchronized(animationList) {
                animationList.toList()
            }
        }


    // warning: tolist access is not thread-safe, use stateMachines instead
    private var stateMachineList =
        Collections.synchronizedList(mutableListOf<StateMachineInstance>())
    val stateMachines: List<StateMachineInstance>
        public get() {
            return synchronized(stateMachineList) {
                stateMachineList.toList()
            }
        }

    var file: File? = null
        private set

    var fit = fit
        set(value) {
            field = value
            // make sure we draw the next frame even if we are not playing right now
            start()
        }

    var alignment = alignment
        set(value) {
            field = value
            // make sure we draw the next frame even if we are not playing right now
            start()
        }

    private val hasPlayingAnimations: Boolean
        get() = playingAnimationSet.isNotEmpty() || playingStateMachineSet.isNotEmpty()

    override fun draw() {
        activeArtboard?.let {
            it.drawSkia(
                cppPointer, fit, alignment
            )
        }
    }

    /// Note: This is happening in the render thread
    /// be aware of thread safety!
    override fun advance(elapsed: Float) {
        activeArtboard?.let { ab ->
            // animations could change, lets cut a list.
            // order of animations is important.....
            animations.forEach { animationInstance ->
                if (playingAnimations.contains(animationInstance)) {
                    val looped = animationInstance.advance(elapsed)

                    animationInstance.apply(ab)
                    if (looped == Loop.ONESHOT) {
                        _stop(animationInstance)
                    } else if (looped != null) {
                        notifyLoop(animationInstance)
                    }
                }
            }

            stateMachines.forEach { stateMachineInstance ->

                if (playingStateMachines.contains(stateMachineInstance)) {
                    val stillPlaying =
                        advanceStateMachineInstance(stateMachineInstance, ab, elapsed)

                    if (!stillPlaying) {
                        // State Machines need to pause not stop
                        // as they have lots of stop and go possibilities
                        _pause(stateMachineInstance)
                    }
                }
            }
            ab.advance(elapsed)
        }
        // Are we done playing?
        if (!hasPlayingAnimations) {
            stopThread()
        }
    }

    private fun advanceStateMachineInstance(
        stateMachineInstance: StateMachineInstance,
        artboard: Artboard,
        elapsed: Float
    ): Boolean {
        val stillPlaying = stateMachineInstance.apply(artboard, elapsed)

        stateMachineInstance.statesChanged.forEach {
            notifyStateChanged(stateMachineInstance, it)
        }
        return stillPlaying
    }

    // PUBLIC FUNCTIONS
    fun setRiveFile(file: File) {
        this.file = file
        selectArtboard()
    }

    fun setArtboardByName(artboardName: String?) {
        if (this.artboardName == artboardName) {
            return
        }

        stopAnimations()
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

    fun artboardBounds(): AABB {
        var output = activeArtboard?.bounds
        if (output == null) {
            output = AABB(0f, 0f)
        }
        return output
    }

    fun clear() {
        playingAnimationSet.clear()
        animationList.clear()
        playingStateMachineSet.clear()
        stateMachineList.clear()
    }

    fun reset() {
        stopAnimations()
        stop()
        clear()
        selectedArtboard?.let {
            setArtboard(it)
        }
        start()
    }

    fun play(
        animationNames: List<String>,
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        areStateMachines: Boolean = false,
        settleInitialState: Boolean = true,
    ) {
        animationNames.forEach {
            _playAnimation(it, loop, direction, areStateMachines, settleInitialState)
        }
    }

    fun play(
        animationName: String,
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        isStateMachine: Boolean = false,
        settleInitialState: Boolean = true,
    ) {
        _playAnimation(animationName, loop, direction, isStateMachine, settleInitialState)
    }

    fun play(
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO, settleInitialState: Boolean = true,
    ) {
        activeArtboard?.let {
            if (it.animationNames.isNotEmpty()) {
                _playAnimation(it.animationNames.first(), loop, direction)
            } else if (it.stateMachineNames.isNotEmpty()) {
                _playAnimation(
                    it.stateMachineNames.first(),
                    loop,
                    direction,
                    settleInitialState
                )
            }
        }
    }

    fun pause() {
        // pause will modify playing animations, so we cut a list of it first.
        playingAnimations.forEach { animation ->
            _pause(animation)
        }
        playingStateMachines.forEach { stateMachine ->
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
        animations.forEach { animation ->
            _stop(animation)
        }
        stateMachines.forEach { stateMachine ->
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
            _play(it, settleStateMachineState = false)
        }
    }

    fun setBooleanState(stateMachineName: String, inputName: String, value: Boolean) {
        val stateMachineInstances = _getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMIBoolean).value = value
            _play(it, settleStateMachineState = false)
        }
    }

    fun setNumberState(stateMachineName: String, inputName: String, value: Float) {
        val stateMachineInstances = _getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMINumber).value = value
            _play(it, settleStateMachineState = false)
        }
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
            activeArtboard?.let {
                val stateMachine = it.stateMachine(animationName)
                val stateMachineInstance = StateMachineInstance(stateMachine)
                stateMachineList.add(stateMachineInstance)
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
        settleInitialState: Boolean = true,
    ) {
        if (isStateMachine) {
            val stateMachineInstances = _getOrCreateStateMachines(animationName)
            stateMachineInstances.forEach { stateMachineInstance ->
                _play(stateMachineInstance, settleInitialState)
            }
        } else {
            val animationInstances = _animations(animationName)
            animationInstances.forEach { animationInstance ->
                _play(animationInstance, loop, direction)
            }
            if (animationInstances.isEmpty()) {
                activeArtboard?.let {
                    val animation = it.animation(animationName)
                    val linearAnimation = LinearAnimationInstance(animation)
                    _play(linearAnimation, loop, direction)
                }
            }
        }
    }

    private fun _play(
        stateMachineInstance: StateMachineInstance,
        settleStateMachineState: Boolean = true
    ) {
        if (!stateMachineList.contains(stateMachineInstance)) {
            stateMachineList.add(stateMachineInstance)
        }

        // Special case:
        // When we start to "play" a state machine, we want it to settle on its initial state
        // otherwise it maybe "stuck" on the Enter state causing issues for fireState triggers.
        // https://2dimensions.slack.com/archives/CLLCU09T6/p1638984141105200
        if (settleStateMachineState) {
            activeArtboard?.let {
                advanceStateMachineInstance(stateMachineInstance, it, 0f)
            }
        }

        playingStateMachineSet.add(stateMachineInstance)
        start()
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
        if (!animationList.contains(animationInstance)) {
            if (direction == Direction.BACKWARDS) {
                animationInstance.time(animationInstance.animation.endTime)
            }
            animationList.add(animationInstance)
        }
        if (direction != Direction.AUTO) {
            animationInstance.direction = direction
        }
        playingAnimationSet.add(animationInstance)
        start()
        notifyPlay(animationInstance)
    }

    private fun _pause(animation: LinearAnimationInstance) {

        val removed = playingAnimationSet.remove(animation)
        if (removed) {
            notifyPause(animation)
        }
    }

    private fun _pause(stateMachine: StateMachineInstance) {
        val removed = playingStateMachineSet.remove(stateMachine)
        if (removed) {
            notifyPause(stateMachine)
        }
    }


    private fun _stop(animation: LinearAnimationInstance) {
        playingAnimationSet.remove(animation)
        val removed = animationList.remove(animation)
        if (removed) {
            notifyStop(animation)
        }
    }

    private fun _stop(stateMachine: StateMachineInstance) {
        playingStateMachineSet.remove(stateMachine)
        val removed = stateMachineList.remove(stateMachine)
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
        this.activeArtboard = artboard.getInstance()

        if (autoplay) {
            animationName?.let {
                play(animationName = it)
            } ?: run {
                stateMachineName?.let {
                    // With autoplay, we default to settling the initial state
                    play(animationName = it, isStateMachine = true, settleInitialState = true)
                } ?: run {
                    // With autoplay, we default to settling the initial state
                    play(settleInitialState = true)
                }

            }
        } else {
            this.activeArtboard?.advance(0f)
            start()
        }
    }

    /* LISTENER INTERFACE */
    interface Listener {
        fun notifyPlay(animation: PlayableInstance)
        fun notifyPause(animation: PlayableInstance)
        fun notifyStop(animation: PlayableInstance)
        fun notifyLoop(animation: PlayableInstance)
        fun notifyStateChanged(stateMachineName: String, stateName: String)
    }

    /* LISTENER OVERRIDES */
    override fun registerListener(listener: RiveArtboardRenderer.Listener) {
        listeners.add(listener)
    }

    override fun unregisterListener(listener: RiveArtboardRenderer.Listener) {
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