package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with the supplied state machine name.
 *
 * @param message A description of the issue.
 */
class StateMachineException(message: String) : RiveException(message)
