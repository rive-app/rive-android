package app.rive.runtime.kotlin.core

enum class RendererType(val value: Int) {
    Rive(0),

    /**
     * Uses the Android Canvas renderer.
     *
     * @deprecated The Canvas renderer is deprecated. Use the Rive renderer instead.
     */
    @Deprecated("The Canvas renderer is deprecated. Use the Rive renderer instead.")
    Canvas(1);

    companion object {
        fun fromIndex(index: Int): RendererType {
            val maxIndex = entries.size
            if (index < 0 || index > maxIndex) {
                throw IndexOutOfBoundsException(
                    "Invalid ${Companion::class.java} index value $index. It must be between 0 and $maxIndex"
                )
            }
            return entries[index]
        }
    }
}
