package app.rive.runtime.kotlin.core

/**
 * [ExitState]s are a base class for state machine layer states.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 */
class ExitState(unsafeCppPointer: Long) : LayerState(unsafeCppPointer) {

    override fun toString(): String {
        return "ExitState"
    }
}
