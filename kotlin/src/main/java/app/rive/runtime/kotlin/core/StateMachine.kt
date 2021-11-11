package app.rive.runtime.kotlin.core

import app.rive.runtime.kotlin.core.errors.*

/**
 * [StateMachine]s as designed in the Rive animation editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [cppPointer] keeps track of this relationship.
 *
 * These can be used with [StateMachineInstance]s and [Artboard]s to draw frames
 *
 * The constructor uses a [cppPointer] to point to its c++ counterpart object.
 */
class StateMachine(val cppPointer: Long) : Playable() {

    private external fun cppName(cppPointer: Long): String
    private external fun cppInputCount(cppPointer: Long): Int
    private external fun cppLayerCount(cppPointer: Long): Int
    private external fun cppStateMachineInputByIndex(cppPointer: Long, index: Int): Long
    private external fun cppStateMachineInputByName(cppPointer: Long, name: String): Long

    /**
     * Return the name given to an animation
     */
    override val name: String
        get() = cppName(cppPointer)

    /**
     * Return the number of inputs configured for the state machine.
     */
    val inputCount: Int
        get() = cppInputCount(cppPointer)

    /**
     * Return the number of layers configured for the state machine.
     */
    val layerCount: Int
        get() = cppLayerCount(cppPointer)

    private fun _convertInput(input: StateMachineInput): StateMachineInput {
        if (input.isBoolean) {
            return StateMachineBooleanInput(input.cppPointer)
        } else if (input.isTrigger) {
            return StateMachineTriggerInput(input.cppPointer)
        } else if (input.isNumber) {
            return StateMachineNumberInput(input.cppPointer)
        }
        throw StateMachineInputException("Unknown State Machine Input for ${input.name}.")
    }

    /**
     * Get the animation at a given [index] in the [Artboard].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun input(index: Int): StateMachineInput {
        val stateMachineInputPointer = cppStateMachineInputByIndex(cppPointer, index)
        if (stateMachineInputPointer == 0L) {
            throw StateMachineInputException("No StateMachineInput found at index $index.")
        }
        return _convertInput(
            StateMachineInput(
                stateMachineInputPointer
            )
        )
    }

    /**
     * Get the animation with a given [name] in the [Artboard].
     */
    @Throws(RiveException::class)
    fun input(name: String): StateMachineInput {
        val stateMachineInputPointer = cppStateMachineInputByName(cppPointer, name)
        if (stateMachineInputPointer == 0L) {
            throw StateMachineInputException("No StateMachineInput found with name $name.")
        }
        return _convertInput(
            StateMachineInput(
                stateMachineInputPointer
            )
        )
    }

    /**
     * Get the stateMachineInputs in the state machine.
     */
    val inputs: List<StateMachineInput>
        get() = (0 until inputCount).map { input(it) }

    /**
     * Get the names of the stateMachineInputs in the state machine.
     */
    val inputNames: List<String>
        get() = (0 until inputCount).map { input(it).name }

    override fun toString(): String {
        return "StateMachine $name\n"
    }
}
