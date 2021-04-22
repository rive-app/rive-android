package app.rive.runtime.kotlin.core

enum class Direction(val value: Int) {
    BACKWARDS(-1),
    FORWARDS(1),
    AUTO(0);

    companion object {
        private val map = values().associateBy(Direction::value)
        fun fromInt(type: Int) = map[type]
    }
}