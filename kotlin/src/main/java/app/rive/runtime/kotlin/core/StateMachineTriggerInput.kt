package app.rive.runtime.kotlin.core

/**
 * [StateMachineTriggerInput]s represents a boolean input
 */
class StateMachineTriggerInput(cppPointer: Long) : StateMachineInput(cppPointer) {

    override fun toString(): String {
        return "StateMachineTriggerInput $name\n"
    }
}
