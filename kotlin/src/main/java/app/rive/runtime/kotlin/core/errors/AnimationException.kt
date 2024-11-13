package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with the supplied animation name.
 *
 * @param message A description of the issue.
 */

class AnimationException(message: String) : RiveException(message)
