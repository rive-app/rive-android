package app.rive.runtime.kotlin.core

/**
 * [BlendState]s are a base class for state machine layer states.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 */
class BlendState(unsafeCppPointer: Long) : LayerState(unsafeCppPointer) {

    override fun toString(): String {
        return "BlendState"
    }
}
