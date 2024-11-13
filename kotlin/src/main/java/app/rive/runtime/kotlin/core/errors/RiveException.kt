package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with some Rive components.
 *
 * @param message A description of the issue.
 */
open class RiveException(message: String) : Exception(message)
