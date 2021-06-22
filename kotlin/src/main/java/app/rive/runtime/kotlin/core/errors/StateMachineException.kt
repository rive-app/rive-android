package app.rive.runtime.kotlin.core.errors

/**
 * A Custom Exception signifying a problem with the State Machine name supplied.
 *
 * Any issue should be described in the [message].
 */
class StateMachineException(message: String) : RiveException(message)
