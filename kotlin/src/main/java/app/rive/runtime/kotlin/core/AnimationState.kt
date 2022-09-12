package app.rive.runtime.kotlin.core

/**
 * [AnimationState]s are a baseclass for state machine layer states.
 *
 * The constructor uses an [unsafeCppPointer] to point to its c++ counterpart object.
 */
class AnimationState(unsafeCppPointer: Long) : LayerState(unsafeCppPointer) {

    private external fun cppName(cppPointer: Long): String

    val name: String
        get() = cppName(cppPointer)

    override fun toString(): String {
        return name
    }
}
