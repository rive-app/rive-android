package app.rive.runtime.kotlin.core

import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.core.errors.StateMachineInputException

/**
 * The [StateMachineInstance] is a helper to wrap common operations to play a [StateMachine].
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [cppPointer] keeps track of this relationship.
 *
 * Use this to keep track of a [StateMachine]s current state and progress. And to help [apply] changes
 * that the [StateMachine] makes to components in an [Artboard].
 */
class StateMachineInstance(val cppPointer: Long) : PlayableInstance() {
    private external fun cppAdvance(
        pointer: Long,
        elapsedTime: Float
    ): Boolean

    private external fun cppInputCount(cppPointer: Long): Int
    private external fun cppSMIInputByIndex(cppPointer: Long, index: Int): Long
    private external fun cppStateChangedCount(cppPointer: Long): Int
    private external fun cppStateChangedByIndex(cppPointer: Long, index: Int): Long
    private external fun cppName(cppPointer: Long): String
    private external fun cppLayerCount(cppPointer: Long): Int

    /**
     * Return the name given to an animation
     */
    override val name: String
        get() = cppName(cppPointer)

    /**
     * Return the number of layers configured for the state machine.
     */
    val layerCount: Int
        get() = cppLayerCount(cppPointer)

    /**
     * Advance the state machine by the [elapsedTime] in seconds.
     * &
     * Applies the state machine instance's current set of transformations to an [artboard].
     *
     * Returns true if the state machine will continue to animate after this advance.
     */
    fun advance( elapsed: Float): Boolean {
        return cppAdvance(cppPointer, elapsed)
    }

    /**
     * Return the number of inputs configured for the state machine.
     */
    val inputCount: Int
        get() = cppInputCount(cppPointer)

    /**
     * Return the number of states changed in the last advance.
     */
    private val stateChangedCount: Int
        get() = cppStateChangedCount(cppPointer)

    private fun convertInput(input: SMIInput): SMIInput {
        return when {
            input.isBoolean -> {
                SMIBoolean(input.cppPointer)
            }
            input.isTrigger -> {
                SMITrigger(input.cppPointer)
            }
            input.isNumber -> {
                SMINumber(input.cppPointer)
            }
            else -> throw StateMachineInputException("Unknown State Machine Input Instance for ${input.name}.")
        }
    }

    /**
     * Get the input instance at a given [index] in the [StateMachine].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun input(index: Int): SMIInput {
        val stateMachineInputPointer = cppSMIInputByIndex(cppPointer, index)
        if (stateMachineInputPointer == 0L) {
            throw StateMachineInputException("No StateMachineInput found at index $index.")
        }
        return convertInput(
            SMIInput(
                stateMachineInputPointer
            )
        )
    }

    /**
     * Get the input with a given [name] in the [StateMachine].
     */
    @Throws(RiveException::class)
    fun input(name: String): SMIInput {
        for (i in 0 until inputCount) {
            val output = input(i)
            if (output.name == name) {
                return output
            }
        }
        throw StateMachineInputException("No StateMachineInput found with name $name.")
    }

    /**
     * Get the stateMachineInputs in the state machine.
     */
    val inputs: List<SMIInput>
        get() = (0 until inputCount).map { input(it) }

    /**
     * Get the names of the stateMachineInputs in the state machine.
     */
    val inputNames: List<String>
        get() = (0 until inputCount).map { input(it).name }

    private fun convertLayerState(state: LayerState): LayerState {
        return when {
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
    }

    /**
     * Get a specific state changed in the last advance.
     */
    @Throws(RiveException::class)
    fun stateChanged(index: Int): LayerState {
        val stateChanged = cppStateChangedByIndex(cppPointer, index)
        if (stateChanged == 0L) {
            throw StateMachineInputException("No StateMachineInput found at index $index.")
        }
        return convertLayerState(
            LayerState(
                stateChanged
            )
        )

    }

    /**
     * Get the layer states changed in the last advance.
     */
    val statesChanged: List<LayerState>
        get() = (0 until stateChangedCount).map { stateChanged(it) }

}


