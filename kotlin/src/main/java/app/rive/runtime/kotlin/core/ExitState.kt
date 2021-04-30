package app.rive.runtime.kotlin.core

/**
 * [ExitState]s are a baseclass for state machine layer states.
 *
 * The constructor uses a [cppPointer] to point to its c++ counterpart object.
 */
class ExitState(cppPointer: Long) : LayerState(cppPointer) {

    override fun toString(): String {
        return "ExitState"
    }
}
