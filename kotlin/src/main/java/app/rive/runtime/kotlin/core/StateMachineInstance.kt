package app.rive.runtime.kotlin.core

import app.rive.runtime.kotlin.core.errors.RiveEventException
import app.rive.runtime.kotlin.core.errors.StateMachineInputException
import java.util.concurrent.locks.ReentrantLock


/**
 * The [StateMachineInstance] is a helper to wrap common operations to play a [StateMachine].
 *
 * This object has a counterpart in C++, which implements a lot of functionality. The
 * [unsafeCppPointer] keeps track of this relationship.
 *
 * Use this to keep track of a [StateMachine]s current state and progress, and to help [apply]
 * changes that the [StateMachine] makes to components in an [Artboard].
 */
class StateMachineInstance(unsafeCppPointer: Long, private val lock: ReentrantLock) :
    PlayableInstance,
    NativeObject(unsafeCppPointer) {
    private external fun cppAdvance(pointer: Long, elapsedTime: Float): Boolean
    private external fun cppInputCount(cppPointer: Long): Int
    private external fun cppSMIInputByIndex(cppPointer: Long, index: Int): Long
    private external fun cppStateChangedCount(cppPointer: Long): Int
    private external fun cppStateChangedByIndex(cppPointer: Long, index: Int): Long
    private external fun cppReportedEventCount(cppPointer: Long): Int
    private external fun cppReportedEventAt(cppPointer: Long, index: Int): RiveEventReport
    private external fun cppName(cppPointer: Long): String
    private external fun cppLayerCount(cppPointer: Long): Int
    private external fun cppPointerDown(cppPointer: Long, x: Float, y: Float)
    private external fun cppPointerUp(cppPointer: Long, x: Float, y: Float)
    private external fun cppPointerMove(cppPointer: Long, x: Float, y: Float)

    external override fun cppDelete(pointer: Long)

    /** @return The name given to an animation. */
    override val name: String
        get() = cppName(cppPointer)

    /** @return The number of layers configured for the state machine. */
    val layerCount: Int
        get() = cppLayerCount(cppPointer)

    /**
     * Advance the state machine.
     *
     * @param elapsed The time in seconds to advance by.
     * @return `true` if the state machine will continue to animate after this advance.
     */
    fun advance(elapsed: Float): Boolean {
        synchronized(lock) { return cppAdvance(cppPointer, elapsed) }
    }

    fun pointerDown(x: Float, y: Float) {
        synchronized(lock) { return cppPointerDown(cppPointer, x, y) }
    }

    fun pointerUp(x: Float, y: Float) {
        synchronized(lock) { return cppPointerUp(cppPointer, x, y) }
    }

    fun pointerMove(x: Float, y: Float) {
        synchronized(lock) { return cppPointerMove(cppPointer, x, y) }
    }

    /** @return The number of inputs configured for the state machine. */
    val inputCount: Int
        get() = cppInputCount(cppPointer)

    /** @return The number of states changed in the last advance. */
    private val stateChangedCount: Int
        get() = cppStateChangedCount(cppPointer)

    /** @return The number of events fired in the last advance. */
    private val reportedEventCount: Int
        get() = cppReportedEventCount(cppPointer)

    private fun convertInput(input: SMIInput): SMIInput {
        val convertedInput = when {
            input.isBoolean -> SMIBoolean(input.cppPointer)
            input.isTrigger -> SMITrigger(input.cppPointer)
            input.isNumber -> SMINumber(input.cppPointer)
            else -> throw StateMachineInputException("Unknown State Machine Input Instance for ${input.name}.")
        }
        return convertedInput
    }

    /**
     * Get the input instance at a given [index] in the state machine.
     *
     * This starts at 0.
     *
     * @throws StateMachineInputException If no [SMIInput] is found at the given [index].
     */
    @Throws(StateMachineInputException::class)
    fun input(index: Int): SMIInput {
        val stateMachineInputPointer = cppSMIInputByIndex(cppPointer, index)
        if (stateMachineInputPointer == NULL_POINTER) {
            throw StateMachineInputException("No StateMachineInput found at index $index.")
        }
        val input = SMIInput(stateMachineInputPointer)
        return convertInput(input)
    }

    /**
     * Get the input with a given [name] in the state machine.
     *
     * @throws StateMachineInputException If no [SMIInput] is found with the given [name].
     */
    @Throws(StateMachineInputException::class)
    fun input(name: String): SMIInput {
        for (i in 0 until inputCount) {
            val output = input(i)
            if (output.name == name) {
                return output
            }
        }
        throw StateMachineInputException("No StateMachineInput found with name $name.")
    }

    /** @return All inputs in the state machine. */
    val inputs: List<SMIInput>
        get() = (0 until inputCount).map { input(it) }

    /** @return The names of all inputs in the state machine. */
    val inputNames: List<String>
        get() = (0 until inputCount).map { input(it).name }

    private fun convertLayerState(state: LayerState): LayerState {
        val convertedState = when {
            state.isAnimationState -> {
                AnimationState(state.cppPointer)
            }

            state.isAnyState -> {
                AnyState(state.cppPointer)
            }

            state.isEntryState -> {
                EntryState(state.cppPointer)
            }

            state.isExitState -> {
                ExitState(state.cppPointer)
            }

            state.isBlendState -> {
                BlendState(state.cppPointer)
            }

            else -> throw StateMachineInputException("Unknown Layer State for ${state}.")
        }
        return convertedState
    }

    /**
     * Get a specific state changed in the last advance.
     *
     * @param index The index of the state.
     * @throws StateMachineInputException If no [LayerState] is found at the given [index].
     */
    @Throws(StateMachineInputException::class)
    fun stateChanged(index: Int): LayerState {
        val stateChanged = cppStateChangedByIndex(cppPointer, index)
        if (stateChanged == 0L) {
            throw StateMachineInputException("No LayerState found at index $index.")
        }
        val layerState = LayerState(stateChanged)
        return convertLayerState(layerState)
    }

    /**
     * Get a specific event fired in the last advance.
     *
     * @param index The index of the event.
     * @throws RiveEventException If no event is found at the given [index].
     */
    @Throws(RiveEventException::class)
    fun eventAt(index: Int): RiveEvent {
        val eventReport = cppReportedEventAt(cppPointer, index)
        if (eventReport.unsafeCppPointer == NULL_POINTER) {
            throw RiveEventException("No Rive Event found at index $index.")
        }

        return eventReport.event
    }

    /** @return All layer states changed in the last advance. */
    val statesChanged: List<LayerState>
        get() = (0 until stateChangedCount).map { stateChanged(it) }


    /** @return All events fired in the last advance. */
    val eventsReported: List<RiveEvent>
        get() = (0 until reportedEventCount).map { eventAt(it) }
}


