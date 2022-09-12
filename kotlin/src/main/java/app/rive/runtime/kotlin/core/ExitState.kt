package app.rive.runtime.kotlin.core

/**
 * [ExitState]s are a baseclass for state machine layer states.
 *
 * The constructor uses an [unsafeCppPointer] to point to its c++ counterpart object.
 */
class ExitState(unsafeCppPointer: Long) : LayerState(unsafeCppPointer) {

    override fun toString(): String {
        return "ExitState"
    }
}
