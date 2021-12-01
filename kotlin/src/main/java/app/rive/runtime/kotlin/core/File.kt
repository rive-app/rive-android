package app.rive.runtime.kotlin.core

import app.rive.runtime.kotlin.core.errors.ArtboardException
import app.rive.runtime.kotlin.core.errors.RiveException

/**
 * [File]s are created in the rive editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [cppPointer] keeps track of this relationship.
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

    private val cppPointer: Long

    init {
        cppPointer = import(bytes, bytes.size)
    }

    private external fun import(bytes: ByteArray, length: Int): Long
    private external fun cppArtboard(cppPointer: Long): Long
    private external fun cppArtboardByName(cppPointer: Long, name: String): Long
    private external fun cppArtboardByIndex(cppPointer: Long, index: Int): Long
    private external fun cppArtboardCount(cppPointer: Long): Int
    private external fun cppDelete(cppPointer: Long)

    /**
     * Get the first artboard in the file.
     */
    val firstArtboard: Artboard
        @Throws(RiveException::class)
        get() {
            val artboardPointer = cppArtboard(cppPointer)
            if (artboardPointer == 0L) {
                throw ArtboardException("No Artboard found.")
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
        val artboardPointer = cppArtboardByName(cppPointer, name)
        if (artboardPointer == 0L) {
            throw ArtboardException(
                "Artboard \"$name\" not found. " +
                        "Available Artboards: ${artboardNames.map { "\"$it\"" }}"
            )
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
        val cppPointer = cppArtboardByIndex(cppPointer, index)
        if (cppPointer == 0L) {
            throw ArtboardException("No Artboard found at index $index.")
        }
        return Artboard(
            cppPointer
        )
    }


    /**
     * Get the number of artboards in the file.
     */
    val artboardCount: Int
        get() = cppArtboardCount(cppPointer)

    /**
     * Get the names of the artboards in the file.
     */
    val artboardNames: List<String>
        get() = (0 until artboardCount).map { artboard(it).name }


    protected fun finalize() {
        if (cppPointer != -1L) {
            cppDelete(cppPointer)
        }
    }
}