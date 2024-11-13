package app.rive.runtime.kotlin.core

/**
 * An open URL Rive event.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 * @see RiveGeneralEvent
 */
class RiveOpenURLEvent(unsafeCppPointer: Long, delay: Float) : RiveEvent(unsafeCppPointer, delay) {
    private external fun cppURL(cppPointer: Long): String
    private external fun cppTarget(cppPointer: Long): String

    /** The URL of the event. */
    val url: String
        get() = cppURL(cppPointer)

    /** The target of the event. */
    val target: String
        get() = cppTarget(cppPointer)

    override fun toString(): String {
        return "OpenURLRiveEvent, name: $name, url: $url, target: $target, properties: $properties"
    }
}
