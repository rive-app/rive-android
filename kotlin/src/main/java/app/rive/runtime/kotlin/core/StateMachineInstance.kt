package app.rive.runtime.kotlin.core

/**
 * The [StateMachineInstance] is a helper to wrap common operations to play a [StateMachine].
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [cppPointer] keeps track of this relationship.
 *
 * Use this to keep track of a [StateMachine]s current state and progress. And to help [apply] changes
 * that the [StateMachine] makes to components in an [Artboard].
 */
class StateMachineInstance(val stateMachine: StateMachine) {
    private var cppPointer: Long = constructor(stateMachine.cppPointer)
    private external fun constructor(stateMachinePointer: Long): Long
    private external fun cppAdvance(pointer: Long, elapsedTime: Float): Boolean
    private external fun cppApply(pointer: Long, artboardPointer: Long)
    private external fun cppInputCount(cppPointer: Long): Int
    private external fun cppSMIInputByIndex(cppPointer: Long, index: Int): Long


    /**
     * Advance the state machine by the [elapsedTime] in seconds.
     *
     * Returns true if the state machine will continue to animate after this advance.
     */
    fun advance(elapsedTime: Float): Boolean {
        return cppAdvance(cppPointer, elapsedTime)
    }

    /**
     * Return the number of inputs configured for the state machine.
     */
    val inputCount: Int
        get() = cppInputCount(cppPointer)

    fun _convertInput(input: SMIInput): SMIInput {
        if (input.isBoolean) {
            return SMIBoolean(input.cppPointer)
        } else if (input.isTrigger) {
            return SMITrigger(input.cppPointer)
        } else if (input.isNumber) {
            return SMINumber(input.cppPointer)
        }
        throw RiveException("Unknown State Machine Input Instance for ${input.name}.")
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
            throw RiveException("No StateMachineInput found at index $index.")
        }
        return _convertInput(SMIInput(
            stateMachineInputPointer
        ))
    }

    /**
     * Get the input with a given [name] in the [StateMachine].
     */
    @Throws(RiveException::class)
    fun input(name: String): SMIInput {
        for (i in 0 until inputCount){
            val output = input(i)
            if (output.name == name){
                return output
            }
        }
        throw RiveException("No StateMachineInput found with name $name.")
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

    /**
     * Applies the state machine instance's current set of transformations to an [artboard].
     */
    fun apply(artboard: Artboard) {
        cppApply(cppPointer, artboard.cppPointer)
    }

}


