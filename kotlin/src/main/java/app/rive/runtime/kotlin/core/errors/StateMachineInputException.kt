package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with a supplied state machine input name.
 *
 * @param message A description of the issue.
 */
class StateMachineInputException(message: String) : RiveException(message)
