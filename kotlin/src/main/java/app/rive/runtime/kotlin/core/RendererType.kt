package app.rive.runtime.kotlin.core


enum class RendererType(val value: Int) {
    Skia(0),
    Rive(1),
    Canvas(2);

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