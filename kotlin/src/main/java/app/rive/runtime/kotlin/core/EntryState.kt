package app.rive.runtime.kotlin.core

/**
 * [EntryState]s are a baseclass for state machine layer states.
 *
 * The constructor uses a [cppPointer] to point to its c++ counterpart object.
 */
class EntryState(cppPointer: Long) : LayerState(cppPointer) {

    override fun toString(): String {
        return "EntryState"
    }
}
