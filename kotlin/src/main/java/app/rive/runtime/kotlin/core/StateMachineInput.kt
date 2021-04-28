package app.rive.runtime.kotlin.core

/**
 * [StateMachineInput]s as designed in the Rive animation editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [nativePointer] keeps track of this relationship.
 *
 * These can be used with [StateMachineInstance]s and [Artboard]s to draw frames
 *
 * The constructor uses a [nativePointer] to point to its c++ counterpart object.
 */
class StateMachineInput(val nativePointer: Long) {

    private external fun nativeName(nativePointer: Long): String
    private external fun nativeIsBoolean(nativePointer: Long): Boolean
    private external fun nativeIsTrigger(nativePointer: Long): Boolean
    private external fun nativeIsNumber(nativePointer: Long): Boolean

    /**
     * Return the name given to an animation
     */
    val name: String
        get() = nativeName(nativePointer)

    /**
     * Is this input a boolean input
     */
    val isBoolean: Boolean
        get() = nativeIsBoolean(nativePointer)

    /**
     * Is this input a boolean input
     */
    val isTrigger: Boolean
        get() = nativeIsTrigger(nativePointer)

    /**
     * Is this input a number input
     */
    val isNumber: Boolean
        get() = nativeIsNumber(nativePointer)


    override fun toString(): String {
        return "StateMachineInput $name\n"
    }
}
