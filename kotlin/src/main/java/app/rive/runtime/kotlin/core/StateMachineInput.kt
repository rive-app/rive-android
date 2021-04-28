package app.rive.runtime.kotlin.core

/**
 * [StateMachineInput]s as designed in the Rive animation editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [cppPointer] keeps track of this relationship.
 *
 * These can be used with [StateMachineInstance]s and [Artboard]s to draw frames
 *
 * The constructor uses a [cppPointer] to point to its c++ counterpart object.
 */
open class StateMachineInput(val cppPointer: Long) {

    private external fun cppName(cppPointer: Long): String
    private external fun cppIsBoolean(cppPointer: Long): Boolean
    private external fun cppIsTrigger(cppPointer: Long): Boolean
    private external fun cppIsNumber(cppPointer: Long): Boolean

    /**
     * Return the name given to an animation
     */
    val name: String
        get() = cppName(cppPointer)

    /**
     * Is this input a boolean input
     */
    val isBoolean: Boolean
        get() = cppIsBoolean(cppPointer)

    /**
     * Is this input a boolean input
     */
    val isTrigger: Boolean
        get() = cppIsTrigger(cppPointer)

    /**
     * Is this input a number input
     */
    val isNumber: Boolean
        get() = cppIsNumber(cppPointer)


    override fun toString(): String {
        return "StateMachineInput $name\n"
    }
}
