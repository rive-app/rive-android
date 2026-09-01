package app.rive.runtime.kotlin.core

/**
 * Configures the playback direction of a linear animation.
 *
 * @deprecated Linear animations are deprecated. Use a state machine to control playback instead.
 */
@Deprecated(
    "Linear animations are deprecated. Use a state machine to control playback instead."
)
enum class Direction(val value: Int) {
    BACKWARDS(-1),
    FORWARDS(1),
    AUTO(0);

    companion object {
        private val map = entries.associateBy(Direction::value)
        fun fromInt(type: Int) = map[type]
    }
}
