package app.rive.runtime.kotlin.core

/**
 * [StateMachineTriggerInput]s represents a boolean input
 */
class StateMachineTriggerInput(nativePointer: Long) : StateMachineInput(nativePointer) {

    override fun toString(): String {
        return "StateMachineTriggerInput $name\n"
    }
}
