package app.rive.runtime.kotlin.core.errors

/**
 * A Custom Exception signifying a problem with a State Machine Input name supplied.
 *
 * Any issue should be described in the [message].
 */
class StateMachineInputException(message: String) : RiveException(message)
