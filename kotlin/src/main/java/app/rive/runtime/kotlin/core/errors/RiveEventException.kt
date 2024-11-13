package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with the supplied event index.
 *
 * @param message A description of the issue.
 */
class RiveEventException(message: String) : RiveException(message)
