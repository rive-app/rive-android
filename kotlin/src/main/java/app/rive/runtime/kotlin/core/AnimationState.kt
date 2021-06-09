package app.rive.runtime.kotlin.core

/**
 * [AnimationState]s are a baseclass for state machine layer states.
 *
 * The constructor uses a [cppPointer] to point to its c++ counterpart object.
 */
class AnimationState(cppPointer: Long) : LayerState(cppPointer) {

    private external fun cppAnimation(cppPointer: Long): Long

    val animation: Animation
        get() = Animation(cppAnimation(cppPointer))

    override fun toString(): String {
        return "${animation.name}"
    }
}
