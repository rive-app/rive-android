package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with a text value run.
 *
 * @param message A description of the issue.
 */
class TextValueRunException(message: String) : RiveException(message)
