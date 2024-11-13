package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with the data in the file.
 *
 * @param message A description of the issue.
 */
class MalformedFileException(message: String) : RiveException(message)
