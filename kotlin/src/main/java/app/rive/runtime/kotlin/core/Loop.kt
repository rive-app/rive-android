package app.rive.runtime.kotlin.core

enum class Loop(val value: Int) {
    ONESHOT(0),
    LOOP(1),
    PINGPONG(2),
    NONE(3);

    companion object {
        private val map = values().associateBy(Loop::value)
        fun fromInt(type: Int) = map[type]
    }
}
