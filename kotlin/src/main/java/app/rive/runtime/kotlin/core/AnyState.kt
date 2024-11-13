package app.rive.runtime.kotlin.core

/**
 * [AnyState]s are a base class for state machine layer states.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 */
class AnyState(unsafeCppPointer: Long) : LayerState(unsafeCppPointer) {

    override fun toString(): String {
        return "AnyState"
    }
}
