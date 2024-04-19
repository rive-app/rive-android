package app.rive.runtime.kotlin.core

enum class Loop {
    ONESHOT, LOOP, PINGPONG, AUTO;

    companion object {
        fun fromIndex(index: Int): Loop {
            val maxIndex = entries.size
            if (index < 0 || index > maxIndex) {
                throw IndexOutOfBoundsException("Invalid Loop index value $index. It must be between 0 and $maxIndex")
            }

            return entries[index]
        }
    }
}
