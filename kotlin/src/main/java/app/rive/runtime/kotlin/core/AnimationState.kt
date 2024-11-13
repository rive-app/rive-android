package app.rive.runtime.kotlin.core

/**
 * [AnimationState]s are a base class for state machine layer states.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 */
class AnimationState(unsafeCppPointer: Long) : LayerState(unsafeCppPointer) {

    private external fun cppName(cppPointer: Long): String

    val name: String
        get() = cppName(cppPointer)

    override fun toString(): String {
        return name
    }
}
