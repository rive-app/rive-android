package app.rive.runtime.kotlin.core

enum class Alignment {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

    companion object {
        fun fromIndex(index: Int): Alignment {
            val maxIndex = Alignment.values().size
            if (index < 0 || index > maxIndex) {
                throw IndexOutOfBoundsException("Invalid Alignment index value $index. It must be between 0 and $maxIndex")
            }

            return Alignment.values()[index]
        }
    }
}