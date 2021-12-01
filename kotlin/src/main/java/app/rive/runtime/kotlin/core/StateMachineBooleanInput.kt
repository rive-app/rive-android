package app.rive.runtime.kotlin.core

/**
 * [StateMachineBooleanInput]s represents a boolean input
 */
class StateMachineBooleanInput(cppPointer: Long) : StateMachineInput(cppPointer) {
    private external fun cppValue(cppPointer: Long): Boolean

    val value: Boolean
        get() = cppValue(cppPointer)

    override fun toString(): String {
        return "StateMachineBooleanInput $name\n"
    }
}
