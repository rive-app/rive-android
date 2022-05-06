package app.rive.runtime.kotlin.core

/**
 * [AnimationState]s are a baseclass for state machine layer states.
 *
 * The constructor uses a [cppPointer] to point to its c++ counterpart object.
 */
class AnimationState(cppPointer: Long) : LayerState(cppPointer) {

    private external fun cppName(cppPointer: Long): String

    val name: String
        get() = cppName(cppPointer)

    override fun toString(): String {
        return name
    }
}
