package app.rive.runtime.kotlin.core

/**
 * [File]s are created in the rive editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [nativePointer] keeps track of this relationship.
 *
 * You can export these .riv files and load them up. [File]s can contain multiple artboards.
 *
 * If the given file cannot be loaded this will throw a [RiveException].
 * The Rive [File] format is evolving, and while we attempt to keep backwards (and forwards) compatibility
 * where possible, there are times when this is not possible.
 *
 * The rive editor will always let you download your file in the latest runtime format.
 */
class File(bytes: ByteArray) {

    private val nativePointer: Long

    init {
        nativePointer = import(bytes, bytes.size)
    }

    private external fun import(bytes: ByteArray, length: Int): Long
    private external fun nativeArtboard(nativePointer: Long): Long
    private external fun nativeArtboardByName(nativePointer: Long, name: String): Long
    private external fun nativeArtboardByIndex(nativePointer: Long, index: Int): Long
    private external fun nativeArtboardCount(nativePointer: Long): Int
    private external fun nativeDelete(nativePointer: Long)

    /**
     * Get the first artboard in the file.
     */
    val firstArtboard: Artboard
        @Throws(RiveException::class)
        get() {
            var artboardPointer = nativeArtboard(nativePointer)
            if (artboardPointer == 0L) {
                throw RiveException("No Artboard found.")
            }

            return Artboard(
                artboardPointer
            )
        }

    /**
     * Get the artboard called [name] in the file.
     *
     * If multiple [Artboard]s have the same [name] it will return the first match.
     */
    @Throws(RiveException::class)
    fun artboard(name: String): Artboard {
        var artboardPointer = nativeArtboardByName(nativePointer, name)
        if (artboardPointer == 0L) {
            throw RiveException("Artboard $name not found.")
        }

        return Artboard(
            artboardPointer
        )
    }

    /**
     * Get the artboard at a given [index] in the [File].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun artboard(index: Int): Artboard {
        var nativePointer = nativeArtboardByIndex(nativePointer, index)
        if (nativePointer == 0L) {
            throw RiveException("No Artboard found at index $index.")
        }
        return Artboard(
            nativePointer
        )
    }


    /**
     * Get the number of artboards in the file.
     */
    val artboardCount: Int
        get() = nativeArtboardCount(nativePointer)


    protected fun finalize() {
        if (nativePointer != -1L) {
            nativeDelete(nativePointer)
        }
    }
}