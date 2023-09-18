package app.rive.runtime.kotlin.core

/**
 * A class for reported Rive events to match its c++ counterpart.
 *
 * The constructor uses an [unsafeCppPointer] to point to the underlying [RiveEvent].
 */
class RiveEventReport(val unsafeCppPointer: Long, secondsDelay: Float) :
    NativeObject(unsafeCppPointer) {
    val event: RiveEvent = convertEvent(RiveEvent(unsafeCppPointer, delay = secondsDelay))

    private fun convertEvent(event: RiveEvent): RiveEvent = when (event.type) {
        EventType.OpenURLEvent -> RiveOpenURLEvent(event.cppPointer, event.delay)
        EventType.GeneralEvent -> RiveGeneralEvent(event.cppPointer, event.delay)
    }
}

/**
 * A class for Rive events.
 *
 * Provides information on Rive events, also see
 * - [RiveOpenURLEvent]
 * - [RiveGeneralEvent]
 *
 * The constructor uses an [unsafeCppPointer] to point to its c++ counterpart object.
 */
open class RiveEvent(unsafeCppPointer: Long, val delay: Float) : NativeObject(unsafeCppPointer) {
    private external fun cppName(cppPointer: Long): String
    private external fun cppType(cppPointer: Long): Short
    private external fun cppProperties(cppPointer: Long): HashMap<String, Any>

    private external fun cppData(cppPointer: Long): HashMap<String, Any>

    /**
     * Name of the event
     */
    val name: String
        get() = cppName(cppPointer)

    private val typeCode: Short
        get() = cppType(cppPointer)

    /**
     * Type of event
     */
    val type: EventType
        get() = EventType.fromInt(typeCode) ?: EventType.GeneralEvent;

    /**
     * Properties attached to the event
     */
    val properties: HashMap<String, Any>
        get() = cppProperties(cppPointer)

    /**
     * Contains all event data
     */
    val data: HashMap<String, Any>
        get() = cppData(cppPointer)

    override fun toString(): String {
        return "RiveEvent $data"
    }
}
