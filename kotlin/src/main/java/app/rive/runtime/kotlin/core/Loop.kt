package app.rive.runtime.kotlin.core

/**
 * Configures how a linear animation behaves when it reaches an endpoint.
 *
 * @deprecated Linear animations are deprecated. Use a state machine to control playback instead.
 */
@Deprecated(
    "Linear animations are deprecated. Use a state machine to control playback instead."
)
enum class Loop {
    ONESHOT, LOOP, PINGPONG, AUTO;

    companion object {
        /**
         * Returns the [Loop] associated to [index].
         *
         * @throws IllegalArgumentException If the index is out of bounds.
         */
        fun fromIndex(index: Int): Loop {
            val maxIndex = entries.size
            if (index < 0 || index > maxIndex) {
                throw IndexOutOfBoundsException("Invalid Loop index value $index. It must be between 0 and $maxIndex")
            }

            return entries[index]
        }
    }
}
