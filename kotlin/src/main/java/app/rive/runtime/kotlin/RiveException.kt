package app.rive.runtime.kotlin

/**
 * A Custom Exception signifying a problem with some Rive components.
 *
 * Any issue should be described in the [message].
 */
class RiveException(message: String) : Exception(message)