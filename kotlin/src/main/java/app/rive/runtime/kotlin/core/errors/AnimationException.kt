package app.rive.runtime.kotlin.core.errors

/**
 * A Custom Exception signifying a problem with the Animation name supplied.
 *
 * Any issue should be described in the [message].
 */
class AnimationException(message: String) : RiveException(message)
