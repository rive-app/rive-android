package app.rive.runtime.kotlin.core

/**
 * A general Rive event.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 * @see RiveOpenURLEvent
 * @deprecated Rive events are deprecated. Use data binding instead.
 */
@Deprecated("Rive events are deprecated. Use data binding instead.")
class RiveGeneralEvent(unsafeCppPointer: Long, delay: Float) : RiveEvent(unsafeCppPointer, delay) {
    override fun toString(): String {
        return "GeneralRiveEvent, name: $name, properties: $properties"
    }
}
