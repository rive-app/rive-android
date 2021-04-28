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
    private external fun nativeLayerCount(nativePointer: Long): Int
    private external fun nativeInputCount(nativePointer: Long): Int

    /**
     * Return the name given to an animation
     */
    val name: String
        get() = nativeName(nativePointer)

    /**
     * Return the number of layers that form the state machine.
     */
    val layerCount: Int
        get() = nativeLayerCount(nativePointer)

    /**
     * Return the number of inputs configured for the state machine.
     */
    val inputCount: Int
        get() = nativeInputCount(nativePointer)

    override fun toString(): String {
        return "StateMachine $name\n"
    }
}
