package app.rive.runtime.kotlin.core

/**
 * The type of Rive event.
 *
 * @deprecated Rive events are deprecated. Use data binding instead.
 */
@Deprecated("Rive events are deprecated. Use data binding instead.")
enum class EventType(val value: Short) {
    OpenURLEvent(131),
    GeneralEvent(128);

    companion object {
        private val map = entries.associateBy(EventType::value)
        fun fromInt(type: Short) = map[type]
    }
}
