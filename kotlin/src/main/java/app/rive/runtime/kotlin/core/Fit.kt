package app.rive.runtime.kotlin.core

enum class Fit {
    FILL, CONTAIN, COVER, FIT_WIDTH, FIT_HEIGHT, NONE, SCALE_DOWN;

    companion object {
        fun fromIndex(index: Int): Fit {
            val maxIndex = entries.size
            if (index < 0 || index > maxIndex) {
                throw IndexOutOfBoundsException("Invalid Fit index value $index. It must be between 0 and $maxIndex")
            }

            return entries[index]
        }
    }
}