package app.rive.runtime.kotlin.core.errors

/**
 * A Custom Exception signifying a problem with the data in the file.
 *
 * Any issue should be described in the [message].
 */
class MalformedFileException(message: String) : RiveException(message)
