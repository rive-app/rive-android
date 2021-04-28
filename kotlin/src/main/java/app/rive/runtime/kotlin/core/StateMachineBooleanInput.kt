package app.rive.runtime.kotlin.core

/**
 * [StateMachineBooleanInput]s represents a boolean input
 */
class StateMachineBooleanInput(nativePointer: Long) : StateMachineInput(nativePointer) {
    private external fun nativeValue(nativePointer: Long): Boolean

    val value:Boolean
        get() = nativeValue(nativePointer)

    override fun toString(): String {
        return "StateMachineBooleanInput $name\n"
    }
}
