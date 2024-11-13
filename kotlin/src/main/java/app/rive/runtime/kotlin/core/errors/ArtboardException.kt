package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with the supplied artboard name.
 *
 * @param message A description of the issue.
 */
class ArtboardException(message: String) : RiveException(message)
