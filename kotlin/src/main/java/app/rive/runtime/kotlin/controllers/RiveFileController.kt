package app.rive.runtime.kotlin.controllers

import android.util.Log
import androidx.annotation.WorkerThread
import app.rive.runtime.kotlin.Observable
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.LayerState
import app.rive.runtime.kotlin.core.LinearAnimationInstance
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.PlayableInstance
import app.rive.runtime.kotlin.core.RefCount
import app.rive.runtime.kotlin.core.SMIBoolean
import app.rive.runtime.kotlin.core.SMINumber
import app.rive.runtime.kotlin.core.SMITrigger
import app.rive.runtime.kotlin.core.StateMachineInstance
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@RequiresOptIn(message = "This API is experimental. It may be changed in the future without notice.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ControllerStateManagement

/**
 * Keeps track of the State of a given Controller so that it can be saved and restored.
 */
@ControllerStateManagement
class ControllerState internal constructor(
    val file: File?,
    val activeArtboard: Artboard?,
    val animations: List<LinearAnimationInstance>,
    val playingAnimations: HashSet<LinearAnimationInstance>,
    val stateMachines: List<StateMachineInstance>,
    val playingStateMachines: HashSet<StateMachineInstance>,
    val isActive: Boolean,
) {
    fun dispose() {
        file?.release()
        activeArtboard?.release()
    }
}

typealias OnStartCallback = () -> Unit

class RiveFileController(
    var fit: Fit = Fit.CONTAIN,
    var alignment: Alignment = Alignment.CENTER,
    var loop: Loop = Loop.AUTO,
    var autoplay: Boolean = true,
    file: File? = null,
    activeArtboard: Artboard? = null,
    var onStart: OnStartCallback? = null,
) : Observable<RiveFileController.Listener>, RefCount {

    companion object {
        const val TAG = "RiveFileController"
    }

    /**
     * How many objects are referencing this Controller.
     * When [refs] > 0 the object will be kept alive with its references
     * When [refs] reaches 0 it will [release] the file, if any.
     */
    override var refs = AtomicInteger(1)

    /**
     * Whether this controller is active or not
     * If this is false, it will prevent advancing or drawing.
     */
    var isActive = false

    var file: File? = file
        set(value) {
            if (value == field) {
                return
            }
            // If we have an old file remove all the old values.
            field?.let {
                reset()
                it.release()
            }
            field = value
            // We only need to acquire the reference to the [file] since all the other components
            // will be fetched from this file (and will, in fact, become a dependency of [file])
            field?.acquire()
        }

    var activeArtboard: Artboard? = activeArtboard
        set(value) {
            if (value == field) {
                return
            }
            field?.release()
            field = value
            field?.acquire()
        }

    // warning: `toList()` access is not thread-safe, use animations instead
    private var animationList =
        Collections.synchronizedList(mutableListOf<LinearAnimationInstance>())
    val animations: List<LinearAnimationInstance>
        get() {
            return synchronized(animationList) {
                animationList.toList()
            }
        }


    // warning: `toList()` access is not thread-safe, use stateMachines instead
    private var stateMachineList =
        Collections.synchronizedList(mutableListOf<StateMachineInstance>())
    val stateMachines: List<StateMachineInstance>
        get() {
            return synchronized(stateMachineList) {
                stateMachineList.toList()
            }
        }

    // warning: toHashSet access is not thread-safe, use playingAnimations instead
    private var playingAnimationSet =
        Collections.synchronizedSet(HashSet<LinearAnimationInstance>())
    val playingAnimations: HashSet<LinearAnimationInstance>
        get() {
            return synchronized(playingAnimationSet) {
                playingAnimationSet.toHashSet()
            }
        }

    // warning: toHashSet access is not thread-safe, use playingStateMachines instead
    private var playingStateMachineSet =
        Collections.synchronizedSet(HashSet<StateMachineInstance>())
    val playingStateMachines: HashSet<StateMachineInstance>
        get() {
            // toHashSet is not thread safe...
            return synchronized(playingStateMachineSet) {
                playingStateMachineSet.toHashSet()
            }
        }

    val pausedAnimations: Set<LinearAnimationInstance>
        get() {
            return animationList subtract playingAnimationSet
        }
    val pausedStateMachines: Set<StateMachineInstance>
        get() {
            return stateMachineList subtract playingStateMachineSet
        }


    val hasPlayingAnimations: Boolean
        get() = playingAnimationSet.isNotEmpty() || playingStateMachineSet.isNotEmpty()


    /**
     * Get a copy of the State of this Controller and acquire a reference to the File to prevent
     * it being released from memory.
     */
    @ControllerStateManagement
    fun saveControllerState(): ControllerState {
        // Acquire the file & artboard to prevent it from being released.
        this.file?.acquire()
        this.activeArtboard?.acquire()
        return ControllerState(
            file,
            activeArtboard,
            // Duplicate contents to grab a reference to the instances.
            animations = animationList.toList(),
            playingAnimations = playingAnimations.toHashSet(),
            stateMachines = stateMachineList.toList(),
            playingStateMachines = playingStateMachines.toHashSet(),
            isActive
        )
    }

    /**
     * Restore a copy of the state on this Controller.
     * It also releases the File reference that was acquired when creating this file.
     */
    @ControllerStateManagement
    fun restoreControllerState(state: ControllerState) {
        // Remove all old values.
        reset()
        // Restore all the previous values.
        file = state.file
        activeArtboard = state.activeArtboard
        state.animations.forEach { animationList.add(it) }
        state.stateMachines.forEach { stateMachineList.add(it) }
        state.playingAnimations.forEach { play(it, it.loop, it.direction) }
        state.playingStateMachines.forEach { play(it) }
        isActive = state.isActive
        // Release the state we had acquired previously to even things out.
        state.dispose()
    }

    /// Note: This is happening in the render thread
    /// be aware of thread safety!
    @WorkerThread
    fun advance(elapsed: Float) {
        activeArtboard?.let { ab ->
            // animations could change, lets cut a list.
            // order of animations is important.....
            animations.forEach { animationInstance ->
                if (playingAnimations.contains(animationInstance)) {
                    val looped = animationInstance.advance(elapsed)
                    animationInstance.apply()

                    if (looped == Loop.ONESHOT) {
                        stop(animationInstance)
                    } else if (looped != null) {
                        notifyLoop(animationInstance)
                    }
                }
            }

            stateMachines.forEach { stateMachineInstance ->
                if (playingStateMachines.contains(stateMachineInstance)) {
                    val stillPlaying =
                        resolveStateMachineAdvance(stateMachineInstance, elapsed)

                    if (!stillPlaying) {
                        pause(stateMachineInstance)
                    }
                }
            }
            ab.advance(elapsed)
            notifyAdvance(elapsed)
        }
    }

    /**
     * Assigns the [file] to this Controller and instances the artboard provided via
     * [artboardName]. If none is provided, it instantiates the first (i.e. default) artboard.
     * If this controller is set to [autoplay] it will also start playing.
     */
    fun setRiveFile(file: File, artboardName: String? = null) {
        if (file == this.file) {
            return
        }
        this.file = file
        // Select the artboard or the first one.
        selectArtboard(artboardName)
    }

    /**
     * Instances the artboard with the specified [name]. If none is provided, it instantiates
     * the first (i.e. default) artboard.
     * If this controller is set to [autoplay] it will also start playing.
     */
    fun selectArtboard(name: String? = null) {
        // Should this warn/throw when called without a File?
        file?.let {
            val artboard = if (name != null) it.artboard(name) else it.firstArtboard
            setArtboard(artboard)
        }
    }

    fun autoplay() {
        if (autoplay) {
            play(settleInitialState = true)
        } else {
            activeArtboard?.advance(0f)
            onStart?.invoke()
        }
    }

    private fun setArtboard(ab: Artboard) {
        activeArtboard = ab
        autoplay()
    }

    /**
     * Use the parameters in [attrs] to initialize [activeArtboard] and any animation/state machine
     * specified by [attrs] animationName or stateMachine name.
     *
     */
    internal fun setupScene(attrs: RiveAnimationView.RendererAttributes) {
        val mFile = file
        if (mFile == null) {
            Log.w(TAG, "Cannot init without a file")
            return
        }
        // If anything has been previously set up, remove it.
        reset()
        autoplay = attrs.autoplay
        val abName = attrs.artboardName

        val artboard = if (abName != null) mFile.artboard(abName) else mFile.firstArtboard
        this.activeArtboard = artboard

        if (autoplay) {
            val animName = attrs.animationName
            val smName = attrs.stateMachineName

            if (animName != null) {
                play(animName)
            } else if (smName != null) {
                play(smName, settleInitialState = true, isStateMachine = true)
            } else {
                play(settleInitialState = true)
            }
        } else {
            activeArtboard?.advance(0f)
            // Schedule a single frame.
            onStart?.invoke()
        }
    }


    fun play(
        animationNames: List<String>,
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        areStateMachines: Boolean = false,
        settleInitialState: Boolean = true,
    ) {
        animationNames.forEach {
            playAnimation(it, loop, direction, areStateMachines, settleInitialState)
        }
    }

    fun play(
        animationName: String,
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        isStateMachine: Boolean = false,
        settleInitialState: Boolean = true,
    ) {
        playAnimation(animationName, loop, direction, isStateMachine, settleInitialState)
    }

    /**
     * Restarts paused animations if there are any.
     * Otherwise, it starts playing the first animation (timeline or state machine) in the Artboard.
     */
    fun play(
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        settleInitialState: Boolean = true,
    ) {
        activeArtboard?.let { activeArtboard ->
            if (pausedAnimations.isNotEmpty() || pausedStateMachines.isNotEmpty()) {
                animations.forEach {
                    play(it, direction = direction, loop = loop)
                }
                stateMachines.forEach {
                    play(it, settleInitialState)
                }
            } else {
                val animationNames = activeArtboard.animationNames
                if (animationNames.isNotEmpty()) {
                    playAnimation(animationNames.first(), loop, direction)
                }
                val stateMachineNames = activeArtboard.stateMachineNames
                if (stateMachineNames.isNotEmpty()) {
                    return playAnimation(
                        stateMachineNames.first(),
                        loop,
                        direction,
                        settleInitialState
                    )
                }
            }
        }
    }

    fun pause() {
        // pause will modify playing animations, so we cut a list of it first.
        playingAnimations.forEach { pause(it) }
        playingStateMachines.forEach { pause(it) }
    }

    fun pause(animationNames: List<String>, areStateMachines: Boolean = false) {
        if (areStateMachines) {
            stateMachines(animationNames).forEach { pause(it) }
        } else {
            animations(animationNames).forEach { pause(it) }
        }
    }

    fun pause(animationName: String, isStateMachine: Boolean = false) {
        if (isStateMachine) {
            stateMachines(animationName).forEach { pause(it) }
        } else {
            animations(animationName).forEach { pause(it) }
        }
    }

    /**
     * called [stopAnimations] to avoid conflicting with [stop]
     */
    fun stopAnimations() {
        // stop will modify animations, so we cut a list of it first.
        if (animationList.isNotEmpty()) {
            animations.forEach { stop(it) }
        }

        if (stateMachineList.isNotEmpty()) {
            stateMachines.forEach { stop(it) }
        }
    }

    fun stopAnimations(animationNames: List<String>, areStateMachines: Boolean = false) {
        if (areStateMachines) {
            stateMachines(animationNames).forEach { stop(it) }
        } else {
            animations(animationNames).forEach { stop(it) }
        }
    }


    fun stopAnimations(animationName: String, isStateMachine: Boolean = false) {
        if (isStateMachine) {
            stateMachines(animationName).forEach { stop(it) }
        } else {

            animations(animationName).forEach { stop(it) }
        }
    }


    fun fireState(stateMachineName: String, inputName: String) {
        val stateMachineInstances = getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMITrigger).fire()
            play(it, settleStateMachineState = false)
        }
    }

    fun setBooleanState(stateMachineName: String, inputName: String, value: Boolean) {
        val stateMachineInstances = getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMIBoolean).value = value
            play(it, settleStateMachineState = false)
        }
    }

    fun setNumberState(stateMachineName: String, inputName: String, value: Float) {
        val stateMachineInstances = getOrCreateStateMachines(stateMachineName)
        stateMachineInstances.forEach {
            (it.input(inputName) as SMINumber).value = value
            play(it, settleStateMachineState = false)
        }
    }

    private fun animations(animationName: String): List<LinearAnimationInstance> {
        return animations(listOf(animationName))
    }

    private fun stateMachines(animationName: String): List<StateMachineInstance> {
        return stateMachines(listOf(animationName))
    }

    private fun animations(animationNames: Collection<String>): List<LinearAnimationInstance> {
        return animations.filter { animationNames.contains(it.name) }
    }

    private fun stateMachines(animationNames: Collection<String>): List<StateMachineInstance> {
        return stateMachines.filter { animationNames.contains(it.name) }
    }

    private fun getOrCreateStateMachines(animationName: String): List<StateMachineInstance> {
        val stateMachineInstances = stateMachines(animationName)
        if (stateMachineInstances.isEmpty()) {
            activeArtboard?.let { activeArtboard ->
                val stateMachineInstance = activeArtboard.stateMachine(animationName)
                stateMachineList.add(stateMachineInstance)
                return listOf(stateMachineInstance)
            }
        }
        return stateMachineInstances
    }

    private fun playAnimation(
        animationName: String,
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        isStateMachine: Boolean = false,
        settleInitialState: Boolean = true,
    ) {
        if (isStateMachine) {
            val stateMachineInstances = getOrCreateStateMachines(animationName)
            stateMachineInstances.forEach { play(it, settleInitialState) }
        } else {
            val animationInstances = animations(animationName)
            animationInstances.forEach { play(it, loop, direction) }
            if (animationInstances.isEmpty()) {
                activeArtboard?.let { activeArtboard ->
                    val animationInstance = activeArtboard.animation(animationName)
                    play(animationInstance, loop, direction)
                }
            }
        }
    }

    private fun resolveStateMachineAdvance(
        stateMachineInstance: StateMachineInstance,
        elapsed: Float
    ): Boolean {

        val stillPlaying = stateMachineInstance.advance(elapsed)
        stateMachineInstance.statesChanged.forEach {
            notifyStateChanged(stateMachineInstance, it)
        }
        return stillPlaying
    }

    internal fun play(
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
            resolveStateMachineAdvance(stateMachineInstance, 0f)
        }

        playingStateMachineSet.add(stateMachineInstance)
        onStart?.invoke()
        notifyPlay(stateMachineInstance)
    }

    internal fun play(
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
                animationInstance.time(animationInstance.endTime)
            }
            animationList.add(animationInstance)
        }
        if (direction != Direction.AUTO) {
            animationInstance.direction = direction
        }
        playingAnimationSet.add(animationInstance)
        onStart?.invoke()
        notifyPlay(animationInstance)
    }


    private fun pause(animation: LinearAnimationInstance) {
        val removed = playingAnimationSet.remove(animation)
        if (removed) {
            notifyPause(animation)
        }
    }

    private fun pause(stateMachine: StateMachineInstance) {
        val removed = playingStateMachineSet.remove(stateMachine)
        if (removed) {
            notifyPause(stateMachine)
        }
    }


    private fun stop(animation: LinearAnimationInstance) {
        playingAnimationSet.remove(animation)
        val removed = animationList.remove(animation)
        if (removed) {
            notifyStop(animation)
        }
    }

    private fun stop(stateMachine: StateMachineInstance) {
        playingStateMachineSet.remove(stateMachine)
        val removed = stateMachineList.remove(stateMachine)
        if (removed) {
            notifyStop(stateMachine)
        }
    }

    // == Listeners ==
    private var listeners = HashSet<Listener>()

    override fun registerListener(listener: Listener) {
        listeners.add(listener)
    }

    override fun unregisterListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyPlay(playableInstance: PlayableInstance) {
        listeners.forEach { it.notifyPlay(playableInstance) }
    }

    private fun notifyPause(playableInstance: PlayableInstance) {
        listeners.forEach { it.notifyPause(playableInstance) }
    }

    private fun notifyStop(playableInstance: PlayableInstance) {
        listeners.forEach { it.notifyStop(playableInstance) }
    }

    private fun notifyLoop(playableInstance: PlayableInstance) {
        listeners.forEach { it.notifyLoop(playableInstance) }
    }

    private fun notifyAdvance(elapsed: Float) {
        listeners.forEach { it.notifyAdvance(elapsed) }
    }

    private fun notifyStateChanged(stateMachine: StateMachineInstance, state: LayerState) {
        listeners.forEach { it.notifyStateChanged(stateMachine.name, state.toString()) }
    }

    /**
     * We want to clear out all references to objects with potentially stale native counterparts
     */
    internal fun reset() {
        playingAnimationSet.clear()
        animationList.clear()
        playingStateMachineSet.clear()
        stateMachineList.clear()
        activeArtboard = null
    }

    /**
     * Release a reference associated with this Controller.
     * If [refs] == 0, then give up all resources and [release] the file
     */
    @Synchronized
    override fun release(): Int {
        val count = super.release()
        require(count >= 0)

        if (count == 0) {
            file = null
        }
        return count
    }

    /* LISTENER INTERFACE */
    interface Listener {
        fun notifyPlay(animation: PlayableInstance)
        fun notifyPause(animation: PlayableInstance)
        fun notifyStop(animation: PlayableInstance)
        fun notifyLoop(animation: PlayableInstance)
        fun notifyStateChanged(stateMachineName: String, stateName: String)
        fun notifyAdvance(elapsed: Float) {}
    }
}