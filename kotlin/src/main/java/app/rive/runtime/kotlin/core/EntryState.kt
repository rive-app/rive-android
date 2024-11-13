package app.rive.runtime.kotlin.core

/**
 * [EntryState]s are a base class for state machine layer states.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 */
class EntryState(unsafeCppPointer: Long) : LayerState(unsafeCppPointer) {

    override fun toString(): String {
        return "EntryState"
    }
}
