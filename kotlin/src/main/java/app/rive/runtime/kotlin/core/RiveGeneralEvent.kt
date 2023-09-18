package app.rive.runtime.kotlin.core

/**
 * A General Rive event.
 *
 * Also see:
 * - [RiveOpenURLEvent]
 *
 * The constructor uses an [unsafeCppPointer] to point to its c++ counterpart object.
 */
class RiveGeneralEvent(unsafeCppPointer: Long, delay: Float) : RiveEvent(unsafeCppPointer, delay) {
    override fun toString(): String {
        return "GeneralRiveEvent, name: $name, properties: $properties"
    }
}