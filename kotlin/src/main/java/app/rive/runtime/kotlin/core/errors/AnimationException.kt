package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with the supplied animation name.
 *
 * @param message A description of the issue.
 * @deprecated Linear animations are deprecated. Use a state machine to control playback instead.
 */
@Deprecated(
    "Linear animations are deprecated. Use a state machine to control playback instead."
)
class AnimationException(message: String) : RiveException(message)
