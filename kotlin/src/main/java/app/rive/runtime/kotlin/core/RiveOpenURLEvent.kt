package app.rive.runtime.kotlin.core

/**
 * An open URL Rive event.
 *
 * Also see:
 * - [RiveGeneralEvent]
 *
 * The constructor uses an [unsafeCppPointer] to point to its c++ counterpart object.
 */
class RiveOpenURLEvent(unsafeCppPointer: Long, delay: Float) : RiveEvent(unsafeCppPointer, delay) {
    private external fun cppURL(cppPointer: Long): String
    private external fun cppTarget(cppPointer: Long): String

    /**
     * Get the URL of the event
     */
    val url: String
        get() = cppURL(cppPointer)

    /**
     * Get the target of the event
     */
    val target: String
        get() = cppTarget(cppPointer)

    override fun toString(): String {
        return "OpenURLRiveEvent, name: $name, url: $url, target: $target, properties: $properties"
    }
}
