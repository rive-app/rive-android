package app.rive.runtime.kotlin.core

/**
 * [StateMachineNumberInput]s represents a boolean input
 */
class StateMachineNumberInput(cppPointer: Long) : StateMachineInput(cppPointer) {
    private external fun cppValue(cppPointer: Long): Float

    val value: Float
        get() = cppValue(cppPointer)

    override fun toString(): String {
        return "StateMachineNumberInput $name\n"
    }
}
