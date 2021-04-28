package app.rive.runtime.kotlin.core

/**
 * [StateMachineNumberInput]s represents a boolean input
 */
class StateMachineNumberInput(nativePointer: Long) : StateMachineInput(nativePointer) {
    private external fun nativeValue(nativePointer: Long): Float

    val value:Float
        get() = nativeValue(nativePointer)

    override fun toString(): String {
        return "StateMachineNumberInput $name\n"
    }
}
