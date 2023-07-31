package app.rive.runtime.kotlin.core.errors

/**
 * A Custom Exception signifying a problem with a text value run.
 *
 * Any issue should be described in the [message].
 */
class TextValueRunException(message: String) : RiveException(message)
