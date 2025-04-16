package app.rive.runtime.kotlin.controllers

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import app.rive.runtime.kotlin.ChangedInput
import app.rive.runtime.kotlin.Observable
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Helpers
import app.rive.runtime.kotlin.core.LayerState
import app.rive.runtime.kotlin.core.LinearAnimationInstance
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.PlayableInstance
import app.rive.runtime.kotlin.core.RefCount
import app.rive.runtime.kotlin.core.RiveEvent
import app.rive.runtime.kotlin.core.SMIBoolean
import app.rive.runtime.kotlin.core.SMINumber
import app.rive.runtime.kotlin.core.SMITrigger
import app.rive.runtime.kotlin.core.StateMachineInstance
import app.rive.runtime.kotlin.renderers.PointerEvents
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

@RequiresOptIn(message = "This API is experimental. It may be changed in the future without notice.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ControllerStateManagement

/** Keeps track of the state of a given controller so that it can be saved and restored. */
@ControllerStateManagement
class ControllerState internal constructor(
    val file: File,
    val activeArtboard: Artboard,
    val animations: List<LinearAnimationInstance>,
    val playingAnimations: HashSet<LinearAnimationInstance>,
    val stateMachines: List<StateMachineInstance>,
    val playingStateMachines: HashSet<StateMachineInstance>,
    val isActive: Boolean,
) {
    fun dispose() {
        file.release()
        activeArtboard.release()
    }
}

typealias OnStartCallback = () -> Unit

class RiveFileController internal constructor(
    var loop: Loop = Loop.AUTO,
    var autoplay: Boolean = true,
    file: File? = null,
    activeArtboard: Artboard? = null,
    var onStart: OnStartCallback? = null,

    @get:VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val changedInputs: ConcurrentLinkedQueue<ChangedInput> = ConcurrentLinkedQueue()
) : Observable<RiveFileController.Listener>, RefCount {

    // The "primary" constructor as the actual primary is internal to expose the queue for testing.
    constructor(
        loop: Loop = Loop.AUTO,
        autoplay: Boolean = true,
        file: File? = null,
        activeArtboard: Artboard? = null,
        onStart: OnStartCallback? = null,
    ) : this(loop, autoplay, file, activeArtboard, onStart, ConcurrentLinkedQueue())

    companion object {
        const val TAG = "RiveFileController"
    }

    /**
     * The number of objects referencing this controller.
     *
     * When [refs] > 0 the object will be kept alive with its references. When [refs] reaches 0 it
     * will [release] the file, if any.
     */
    override var refs = AtomicInteger(1)

    /**
     * Whether this controller is active or not. If this is false, it will prevent advancing or
     * drawing.
     */
    var isActive = false

    /**
     * Whether this [activeArtboard] requires resizing or not. If this is true, the artboard will be
     * resized in the next draw call.
     */
    internal var requireArtboardResize = AtomicBoolean(false);

    var fit: Fit = Fit.CONTAIN
        set(value) {
            field = value
            requireArtboardResize.set(true);
            synchronized(startStopLock) {
                onStart?.invoke()
            }
        }
    var alignment: Alignment = Alignment.CENTER
        set(value) {
            field = value
            synchronized(startStopLock) {
                onStart?.invoke()
            }
        }

    /**
     * The scale factor to use for Fit.LAYOUT. If null, it will use a density determined by Rive
     * (automatic). See [RiveFileController.layoutScaleFactorAutomatic] for more details.
     */
    var layoutScaleFactor: Float? = null
        set(value) {
            field = value
            requireArtboardResize.set(true);
            synchronized(startStopLock) {
                onStart?.invoke()
            }
        }

    /**
     * The automatic scale factor set by Rive. This value will only be used if [layoutScaleFactor]
     * is not set (null).
     */
    var layoutScaleFactorAutomatic: Float = 1.0f
        internal set(value) {
            field = value
            requireArtboardResize.set(true);
            synchronized(startStopLock) {
                onStart?.invoke()
            }
        }

    /**
     * The active scale factor to use for Fit.LAYOUT. If the user has set a scale factor, it will
     * use that. Otherwise, it will use the automatic scale factor set by Rive.
     */
    internal val layoutScaleFactorActive: Float
        get() = layoutScaleFactor ?: layoutScaleFactorAutomatic

    var file: File? = file
        set(value) {
            if (value == field) {
                return
            }

            synchronized(field?.lock ?: this) {
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
        }

    var activeArtboard: Artboard? = activeArtboard
        set(value) {
            if (value == field) {
                return
            }
            synchronized(file?.lock ?: this) {
                field?.release()
                field = value
                field?.acquire()
                userSetVolume?.let { activeArtboard?.volume = it }
            }
        }

    // Warning: `toList()` access is not thread-safe, use animations instead
    private var animationList =
        Collections.synchronizedList(mutableListOf<LinearAnimationInstance>())
    val animations: List<LinearAnimationInstance>
        get() {
            return synchronized(animationList) {
                animationList.toList()
            }
        }


    // Warning: `toList()` access is not thread-safe, use stateMachines instead
    private var stateMachineList =
        Collections.synchronizedList(mutableListOf<StateMachineInstance>())
    val stateMachines: List<StateMachineInstance>
        get() {
            return synchronized(stateMachineList) {
                stateMachineList.toList()
            }
        }

    // Warning: toHashSet access is not thread-safe, use playingAnimations instead
    private var playingAnimationSet =
        Collections.synchronizedSet(HashSet<LinearAnimationInstance>())
    val playingAnimations: HashSet<LinearAnimationInstance>
        get() {
            return synchronized(playingAnimationSet) {
                playingAnimationSet.toHashSet()
            }
        }

    // Warning: toHashSet access is not thread-safe, use playingStateMachines instead
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
            return animations subtract playingAnimations
        }
    val pausedStateMachines: Set<StateMachineInstance>
        get() {
            return stateMachines subtract playingStateMachines
        }

    /** Lock to prevent race conditions on starting and stopping the rendering thread. */
    internal val startStopLock = ReentrantLock()

    val isAdvancing: Boolean
        get() = playingAnimationSet.isNotEmpty() || playingStateMachineSet.isNotEmpty() || changedInputs.isNotEmpty()

    val artboardBounds: RectF
        get() = activeArtboard?.bounds ?: RectF()

    var targetBounds: RectF = RectF()


    /**
     * Get a copy of the state of this controller and acquire a reference to the file to prevent it
     * being released from memory.
     *
     * Returns a [ControllerState] object with everything the controller was using. If the
     * controller is pointing to stale data, it will return null.
     */
    @ControllerStateManagement
    fun saveControllerState(): ControllerState? {
        val mFile = this.file ?: return null
        val mArtboard = this.activeArtboard ?: return null
        synchronized(mFile.lock) {
            // This resource had already been released.
            if (!mFile.hasCppObject) {
                return null
            }
            // Acquire the file & artboard to prevent dispose().
            mFile.acquire()
            mArtboard.acquire()

            return ControllerState(
                mFile,
                mArtboard,
                // Duplicate contents to grab a reference to the instances.
                animations = animationList.toList(),
                playingAnimations = playingAnimations.toHashSet(),
                stateMachines = stateMachineList.toList(),
                playingStateMachines = playingStateMachines.toHashSet(),
                isActive
            )
        }
    }

    /**
     * Restore a copy of the state to this Controller.
     *
     * It also [release()]s any resources currently associated with this Controller in favor of the
     * ones stored on the [state].
     */
    @ControllerStateManagement
    fun restoreControllerState(state: ControllerState) {
        synchronized(file?.lock ?: this) {
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
    }

    /**
     * Note: This is happening in the render thread: this function is synchronized with a
     * ReentrantLock stored on the [File] because this is a critical section with the UI thread.
     * When advancing or performing operations around an animations data structure, be conscious of
     * thread safety.
     */
    @WorkerThread
    fun advance(elapsed: Float) {
        // We need a file to advance.
        val mLock = this.file?.lock ?: return
        synchronized(mLock) {
            activeArtboard?.let { ab ->
                // Process all the inputs right away.
                processAllInputs()

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

                val stateMachinesToPause = mutableListOf<StateMachineInstance>()
                stateMachines.forEach { stateMachineInstance ->
                    if (playingStateMachines.contains(stateMachineInstance)) {
                        val stillPlaying =
                            resolveStateMachineAdvance(stateMachineInstance, elapsed)

                        if (!stillPlaying) {
                            stateMachinesToPause.add(stateMachineInstance)
                        }
                    }
                }

                // Only remove the state machines if the elapsed time was
                // greater than 0. 0 elapsed time causes no changes so it's
                // no-op advance.
                if (elapsed > 0.0) {
                    stateMachinesToPause.forEach { pause(stateMachine = it) }
                }

                // Poll the assigned view model instances for changes.
                playingStateMachines.mapNotNull { it.viewModelInstance }
                    .forEach { it.pollChanges() }

                notifyAdvance(elapsed)
            }
        }
    }

    /**
     * Assigns the [file] to this Controller and instances the artboard provided via [artboardName].
     * If none is provided, it instantiates the first (i.e. default) artboard. If this controller is
     * set to [autoplay] it will also start playing.
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
     * Instances the artboard with the specified [name]. If none is provided, it instantiates the
     * first (i.e. default) artboard. If this controller is set to [autoplay] it will also start
     * playing.
     */
    fun selectArtboard(name: String? = null) {
        file?.let {
            val artboard = if (name != null) it.artboard(name) else it.firstArtboard
            setArtboard(artboard)
        } ?: run {
            Log.w(TAG, "selectArtboard: cannot select an Artboard without a valid File.")
        }
    }

    fun autoplay() {
        if (autoplay) {
            play(settleInitialState = true)
        } else {
            // advance() locks on the file lock internally
            activeArtboard?.advance(0f)
            synchronized(startStopLock) { onStart?.invoke() }
        }
    }

    private fun setArtboard(ab: Artboard) {
        if (ab == activeArtboard) return
        stopAnimations()
        activeArtboard = ab
        autoplay()
    }

    /**
     * Use the parameters in [rendererAttributes] to initialize [activeArtboard] and any
     * animation/state machine specified by [rendererAttributes] animationName or stateMachine name.
     */
    internal fun setupScene(rendererAttributes: RiveAnimationView.RendererAttributes) {
        val mFile = file
        if (mFile == null) {
            Log.w(TAG, "Cannot init without a file")
            return
        }
        // If anything has been previously set up, remove it.
        reset()
        autoplay = rendererAttributes.autoplay
        alignment = rendererAttributes.alignment
        fit = rendererAttributes.fit
        loop = rendererAttributes.loop

        val abName = rendererAttributes.artboardName

        activeArtboard = if (abName != null) mFile.artboard(abName) else mFile.firstArtboard

        if (rendererAttributes.autoBind && activeArtboard != null) {
            val activeArtboard = activeArtboard!!
            val defaultInstance =
                mFile.defaultViewModelForArtboard(activeArtboard).createDefaultInstance()
            activeArtboard.viewModelInstance = defaultInstance

            // Since state machines aren't created until play(),
            // we need to check if they need to be created now.
            val stateMachineName = rendererAttributes.stateMachineName
                ?: activeArtboard.stateMachineNames.firstOrNull()
            stateMachineName?.let { getOrCreateStateMachines(it) }
            stateMachines.forEach { it.viewModelInstance = defaultInstance }
        }

        if (autoplay) {
            val animName = rendererAttributes.animationName
            val smName = rendererAttributes.stateMachineName

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
            synchronized(startStopLock) { onStart?.invoke() }
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
            playAnimation(
                animationName = it,
                loop = loop,
                direction = direction,
                isStateMachine = areStateMachines,
                settleInitialState = settleInitialState
            )
        }
    }

    fun play(
        animationName: String,
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        isStateMachine: Boolean = false,
        settleInitialState: Boolean = true,
    ) {
        playAnimation(
            animationName = animationName,
            loop = loop,
            direction = direction,
            isStateMachine = isStateMachine,
            settleInitialState = settleInitialState
        )
    }

    /**
     * Restarts paused animations if there are any. Otherwise, it starts playing the first animation
     * (timeline or state machine) in the Artboard.
     */
    fun play(
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        settleInitialState: Boolean = true,
    ) {
        activeArtboard?.let { activeArtboard ->
            if (pausedAnimations.isNotEmpty() || pausedStateMachines.isNotEmpty()) {
                animations.forEach {
                    play(animationInstance = it, direction = direction, loop = loop)
                }
                stateMachines.forEach {
                    play(stateMachineInstance = it, settleStateMachineState = settleInitialState)
                }
            } else {
                val animationNames = activeArtboard.animationNames
                if (animationNames.isNotEmpty()) {
                    playAnimation(
                        animationName = animationNames.first(),
                        loop = loop,
                        direction = direction
                    )
                }
                val stateMachineNames = activeArtboard.stateMachineNames
                if (stateMachineNames.isNotEmpty()) {
                    return playAnimation(
                        animationName = stateMachineNames.first(),
                        loop = loop,
                        direction = direction,
                        isStateMachine = true,
                        settleInitialState = settleInitialState
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
            stateMachines(animationName).forEach { pause(stateMachine = it) }
        } else {
            animations(animationName).forEach { pause(animation = it) }
        }
    }

    /** Named [stopAnimations] to avoid conflicting with [stop]. */
    fun stopAnimations() {
        // Check whether we need to go through the synchronized `animations` getter
        if (animationList.isNotEmpty()) {
            animations.forEach { stop(it) }
        }

        // Check whether we need to go through the synchronized `stateMachines` getter
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

    /**
     * Queues an input with [inputName] so that it can be processed on the next advance. It also
     * tries to start the thread if it's not started, which will internally cause advance to happen
     * at least once.
     */
    private fun queueInput(
        stateMachineName: String,
        inputName: String,
        value: Any? = null,
        path: String? = null,
    ) {
        queueInputs(
            ChangedInput(
                stateMachineName = stateMachineName,
                name = inputName,
                value = value,
                nestedArtboardPath = path
            )
        )
    }

    internal fun queueInputs(vararg inputs: ChangedInput) {
        // `synchronize(startStopLock)` so the UI thread will not attempt starting while the worker
        // thread is still deciding to stop.
        // If this happened during an advance, we could potentially miss an input if `isAdvancing`
        // has already been checked and the worker thread stopped.
        synchronized(startStopLock) {
            changedInputs.addAll(inputs)
            // Restart the thread if needed.
            onStart?.invoke()
        }
    }

    @WorkerThread
    private fun processAllInputs() {
        // Gather all state machines that need playing and do that only once.
        val playableSet = mutableSetOf<StateMachineInstance>()
        // No need to lock this: this is being called from `advance()` which is `synchronized(file)`
        while (changedInputs.isNotEmpty()) {
            // There is a small chance that the queue will be emptied by another thread before removing.
            // Null checking the removed item protects against that scenario.
            val input = changedInputs.poll() ?: break
            if (input.nestedArtboardPath == null) {
                val stateMachines = getOrCreateStateMachines(input.stateMachineName)
                stateMachines.forEach { stateMachineInstance ->
                    playableSet.add(stateMachineInstance)
                    when (val smiInput = stateMachineInstance.input(input.name)) {
                        is SMITrigger -> smiInput.fire()
                        is SMIBoolean -> smiInput.value = input.value as Boolean
                        is SMINumber -> smiInput.value = input.value as Float
                    }
                }
            } else {
                when (val smiInput = activeArtboard?.input(input.name, input.nestedArtboardPath)) {
                    is SMITrigger -> {
                        smiInput.fire()
                    }

                    is SMIBoolean -> {
                        smiInput.value = input.value as Boolean
                    }

                    is SMINumber -> {
                        smiInput.value = input.value as Float
                    }
                }
            }
        }
        playableSet.forEach { play(it, settleStateMachineState = false) }
    }

    fun fireState(stateMachineName: String, inputName: String, path: String? = null) {
        queueInput(stateMachineName = stateMachineName, inputName = inputName, path = path)
    }

    fun setBooleanState(
        stateMachineName: String,
        inputName: String,
        value: Boolean,
        path: String? = null,
    ) {
        queueInput(
            stateMachineName = stateMachineName,
            inputName = inputName,
            value = value,
            path = path
        )
    }

    fun setNumberState(
        stateMachineName: String,
        inputName: String,
        value: Float,
        path: String? = null,
    ) {
        queueInput(
            stateMachineName = stateMachineName,
            inputName = inputName,
            value = value,
            path = path
        )
    }

    fun fireStateAtPath(inputName: String, path: String) {
        queueInput(stateMachineName = "", inputName = inputName, path = path)
    }

    fun setBooleanStateAtPath(inputName: String, value: Boolean, path: String) {
        queueInput(stateMachineName = "", inputName = inputName, value = value, path = path)
    }

    fun setNumberStateAtPath(inputName: String, value: Float, path: String) {
        queueInput(stateMachineName = "", inputName = inputName, value = value, path = path)
    }

    /**
     * Get the current value for a text run named [textRunName] on the active artboard if it exists.
     */
    fun getTextRunValue(textRunName: String): String? {
        return activeArtboard?.getTextRunValue(textRunName)
    }

    /**
     * Get the text value for a text run named [textRunName] on the nested artboard represented at
     * [path].
     */
    fun getTextRunValue(textRunName: String, path: String): String? {
        return activeArtboard?.getTextRunValue(textRunName, path)
    }

    /**
     * Set the text value for a text run named [textRunName] to [textValue] on the active artboard.
     *
     * @throws TextValueRunException if the text run does not exist.
     */
    fun setTextRunValue(textRunName: String, textValue: String) {
        activeArtboard?.setTextRunValue(textRunName, textValue)
        stateMachines.forEach {
            play(it, settleStateMachineState = false)
        }
    }

    /**
     * Set the text value for a text run named [textRunName] to [textValue] on the nested artboard
     * represented at [path].
     *
     * @throws TextValueRunException if the text run does not exist.
     */
    fun setTextRunValue(textRunName: String, textValue: String, path: String) {
        activeArtboard?.setTextRunValue(textRunName, textValue, path)
        stateMachines.forEach {
            play(it, settleStateMachineState = false)
        }
    }

    private var userSetVolume: Float? = null // Default value

    /** Get the active [Artboard]'s volume. */
    fun getVolume(): Float? = activeArtboard?.volume

    /** Set the active [Artboard]'s volume to [value]. */
    fun setVolume(value: Float) {
        userSetVolume = value
        activeArtboard?.volume = value
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
        elapsed: Float,
    ): Boolean {
        if (eventListeners.isNotEmpty()) {
            stateMachineInstance.eventsReported.forEach {
                notifyEvent(it)
            }
        }
        val stillPlaying = stateMachineInstance.advance(elapsed)
        if (listeners.isNotEmpty()) {
            stateMachineInstance.statesChanged.forEach {
                notifyStateChanged(stateMachineInstance, it)
            }
        }
        return stillPlaying
    }

    internal fun play(
        stateMachineInstance: StateMachineInstance,
        settleStateMachineState: Boolean = true,
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

        synchronized(startStopLock) {
            playingStateMachineSet.add(stateMachineInstance)
            onStart?.invoke()
        }
        notifyPlay(stateMachineInstance)
    }

    internal fun play(
        animationInstance: LinearAnimationInstance,
        loop: Loop,
        direction: Direction,
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
        synchronized(startStopLock) {
            playingAnimationSet.add(animationInstance)
            onStart?.invoke()
        }
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


    fun pointerEvent(eventType: PointerEvents, x: Float, y: Float) {
        /// TODO: once we start composing artboards we may need x,y offsets here...
        val artboardEventLocation = Helpers.convertToArtboardSpace(
            touchBounds = targetBounds,
            touchLocation = PointF(x, y),
            fit = fit,
            alignment = alignment,
            artboardBounds = activeArtboard?.bounds ?: RectF(),
            scaleFactor = layoutScaleFactorActive
        )
        stateMachines.forEach {

            when (eventType) {
                PointerEvents.POINTER_DOWN -> it.pointerDown(
                    artboardEventLocation.x,
                    artboardEventLocation.y
                )

                PointerEvents.POINTER_UP -> it.pointerUp(
                    artboardEventLocation.x,
                    artboardEventLocation.y
                )

                PointerEvents.POINTER_MOVE -> it.pointerMove(
                    artboardEventLocation.x,
                    artboardEventLocation.y
                )

            }
            play(it, settleStateMachineState = false)
        }
    }

    // == Listeners ==
    private var _listeners: MutableSet<Listener> = Collections.synchronizedSet(HashSet<Listener>())

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val listeners: HashSet<Listener>
        get() {
            return synchronized(_listeners) { _listeners.toHashSet() }
        }

    private var _eventListeners: MutableSet<RiveEventListener> =
        Collections.synchronizedSet(HashSet<RiveEventListener>())

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val eventListeners: HashSet<RiveEventListener>
        get() {
            return synchronized(_eventListeners) { _eventListeners.toHashSet() }
        }

    override fun registerListener(listener: Listener) {
        synchronized(startStopLock) {
            _listeners.add(listener)
        }
    }

    override fun unregisterListener(listener: Listener) {
        synchronized(startStopLock) {
            _listeners.remove(listener)
        }
    }

    /**
     * Adds a [RiveEventListener] to get notified on [RiveEvent]s.
     *
     * Remove with: [removeEventListener].
     */
    fun addEventListener(listener: RiveEventListener) {
        synchronized(startStopLock) {
            _eventListeners.add(listener)
        }
    }

    /** Removes the [listener]. */
    fun removeEventListener(listener: RiveEventListener) {
        synchronized(startStopLock) {
            _eventListeners.remove(listener)
        }
    }

    private fun notifyPlay(playableInstance: PlayableInstance) {
        listeners.toList().forEach { it.notifyPlay(playableInstance) }
    }

    private fun notifyPause(playableInstance: PlayableInstance) {
        listeners.toList().forEach { it.notifyPause(playableInstance) }
    }

    private fun notifyStop(playableInstance: PlayableInstance) {
        listeners.toList().forEach { it.notifyStop(playableInstance) }
    }

    private fun notifyLoop(playableInstance: PlayableInstance) {
        listeners.toList().forEach { it.notifyLoop(playableInstance) }
    }

    @WorkerThread
    private fun notifyAdvance(elapsed: Float) {
        listeners.toList().forEach { it.notifyAdvance(elapsed) }
    }

    private fun notifyStateChanged(stateMachine: StateMachineInstance, state: LayerState) {
        listeners.toList().forEach { it.notifyStateChanged(stateMachine.name, state.toString()) }
    }

    private fun notifyEvent(event: RiveEvent) {
        eventListeners.toList().forEach { it.notifyEvent(event) }
    }

    /**
     * We want to clear out all references to objects with potentially stale native counterparts.
     */
    internal fun reset() {
        playingAnimationSet.clear()
        animationList.clear()
        playingStateMachineSet.clear()
        stateMachineList.clear()
        changedInputs.clear()
        activeArtboard = null
    }

    /**
     * Release a reference associated with this Controller. If [refs] == 0, then give up all
     * resources and [release] the file.
     *
     * @throws IllegalStateException If [refs] is 0
     */
    @Throws(IllegalStateException::class)
    override fun release(): Int {
        val count = super.release()
        require(count >= 0)

        if (count == 0) {
            require(!isActive)
            // Will `release()` the file if one was set
            file = null
        }
        return count
    }

    interface Listener {
        fun notifyPlay(animation: PlayableInstance)
        fun notifyPause(animation: PlayableInstance)
        fun notifyStop(animation: PlayableInstance)
        fun notifyLoop(animation: PlayableInstance)
        fun notifyStateChanged(stateMachineName: String, stateName: String)
        fun notifyAdvance(elapsed: Float) {}
    }

    interface RiveEventListener {
        fun notifyEvent(event: RiveEvent)
    }
}