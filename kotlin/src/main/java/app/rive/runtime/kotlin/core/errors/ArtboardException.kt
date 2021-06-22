package app.rive.runtime.kotlin.core.errors

/**
 * A Custom Exception signifying a problem with the Artboard name supplied.
 *
 * Any issue should be described in the [message].
 */
class ArtboardException(message: String) : RiveException(message)
