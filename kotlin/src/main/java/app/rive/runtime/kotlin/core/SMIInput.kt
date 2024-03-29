package app.rive.runtime.kotlin.core

/**
 * [SMIInput]s are a baseclass for state machine input instances.
 *
 * These instances allow modification of the state of the attached state machine.
 *
 * The constructor uses an [unsafeCppPointer] to point to its c++ counterpart object.
 */
open class SMIInput(unsafeCppPointer: Long) : NativeObject(unsafeCppPointer) {
    //    SMIInput cpp objects are tied to the lifecycle of hte StateMachineInstances in cpp
    //    Therefore we do not have a cppDelete implementation
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
        return "SMIInput $name\n"
    }
}
