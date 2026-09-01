package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with a supplied state machine input name.
 *
 * @param message A description of the issue.
 * @deprecated State machine inputs are deprecated. Use data binding properties instead.
 */
@Deprecated("State machine inputs are deprecated. Use data binding properties instead.")
class StateMachineInputException(message: String) : RiveException(message)
