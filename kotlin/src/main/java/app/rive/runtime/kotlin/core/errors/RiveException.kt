package app.rive.runtime.kotlin.core.errors

/**
 * A Custom Exception signifying a problem with some Rive components.
 *
 * Any issue should be described in the [message].
 */
open class RiveException(message: String) : Exception(message)
