package app.rive.runtime.kotlin.core

/**
 * [StateMachine]s as designed in the Rive animation editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [nativePointer] keeps track of this relationship.
 *
 * These can be used with [StateMachineInstance]s and [Artboard]s to draw frames
 *
 * The constructor uses a [nativePointer] to point to its c++ counterpart object.
 */
class StateMachine(val nativePointer: Long) {

    private external fun nativeName(nativePointer: Long): String
    private external fun nativeInputCount(nativePointer: Long): Int
    private external fun nativeLayerCount(nativePointer: Long): Int
    private external fun nativeStateMachineInputByIndex(nativePointer: Long, index: Int): Long
    private external fun nativeStateMachineInputByName(nativePointer: Long, name: String): Long

    /**
     * Return the name given to an animation
     */
    val name: String
        get() = nativeName(nativePointer)

    /**
     * Return the number of inputs configured for the state machine.
     */
    val inputCount: Int
        get() = nativeInputCount(nativePointer)

    /**
     * Return the number of layers configured for the state machine.
     */
    val layerCount: Int
        get() = nativeLayerCount(nativePointer)

    /**
     * Get the animation at a given [index] in the [Artboard].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun input(index: Int): StateMachineInput {
        var stateMachineInputPointer = nativeStateMachineInputByIndex(nativePointer, index)
        if (stateMachineInputPointer == 0L) {
            throw RiveException("No StateMachineInput found at index $index.")
        }
        return StateMachineInput(
            stateMachineInputPointer
        )
    }

    /**
     * Get the animation with a given [name] in the [Artboard].
     */
    @Throws(RiveException::class)
    fun input(name: String): StateMachineInput {
        var stateMachineInputPointer = nativeStateMachineInputByName(nativePointer, name)
        if (stateMachineInputPointer == 0L) {
            throw RiveException("No StateMachineInput found with name $name.")
        }
        return StateMachineInput(
            stateMachineInputPointer
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
