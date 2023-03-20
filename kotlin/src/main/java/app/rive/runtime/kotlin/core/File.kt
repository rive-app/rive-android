package app.rive.runtime.kotlin.core

import app.rive.runtime.kotlin.core.errors.ArtboardException
import app.rive.runtime.kotlin.core.errors.RiveException

/**
 * [File]s are created in the rive editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [unsafeCppPointer] keeps track of this relationship.
 *
 * You can export these .riv files and load them up. [File]s can contain multiple artboards.
 *
 * If the given file cannot be loaded this will throw a [RiveException].
 * The Rive [File] format is evolving, and while we attempt to keep backwards (and forwards) compatibility
 * where possible, there are times when this is not possible.
 *
 * The rive editor will always let you download your file in the latest runtime format.
 */
class File(bytes: ByteArray) : NativeObject(NULL_POINTER) {
    private val createBytes: ByteArray

    init {
        createBytes = bytes
        cppPointer = import(bytes, bytes.size)
    }

    private external fun import(bytes: ByteArray, length: Int): Long
    private external fun cppArtboardByName(cppPointer: Long, name: String): Long
    private external fun cppArtboardByIndex(cppPointer: Long, index: Int): Long
    private external fun cppArtboardNameByIndex(cppPointer: Long, index: Int): String
    private external fun cppArtboardCount(cppPointer: Long): Int

    external override fun cppDelete(pointer: Long)

    /**
     * Hydrate the file, and ensure we have loaded the [createBytes] into our cpp representation
     * returns [Boolean] to indicate if it needed hydration
     *
     * We need this because File objects get garbage collected separately from our cpp objects
     */
    fun hydrate(): Boolean {
        if (!hasCppObject) {
            cppPointer = import(createBytes, createBytes.size)
            return true
        }
        return false
    }

    /**
     * Get the first artboard in the file.
     */
    val firstArtboard: Artboard
        @Throws(RiveException::class)
        get() = artboard(0)

    /**
     * Get the artboard called [name] in the file.
     *
     * If multiple [Artboard]s have the same [name] it will return the first match.
     */
    @Throws(RiveException::class)
    fun artboard(name: String): Artboard {
        // Creates a new artboard instance.
        val artboardPointer = cppArtboardByName(cppPointer, name)
        if (artboardPointer == NULL_POINTER) {
            throw ArtboardException(
                "Artboard \"$name\" not found. " +
                        "Available Artboards: ${artboardNames.map { "\"$it\"" }}"
            )
        }

        val ab = Artboard(artboardPointer)
        dependencies.add(ab)
        return ab
    }

    /**
     * Get the artboard at a given [index] in the [File].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun artboard(index: Int): Artboard {
        // Creates a new Artboard instance.
        val artboardPointer = cppArtboardByIndex(cppPointer, index)
        if (artboardPointer == NULL_POINTER) {
            throw ArtboardException("No Artboard found at index $index.")
        }
        val ab = Artboard(artboardPointer)
        dependencies.add(ab)
        return ab
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
        get() = (0 until artboardCount).map {
            val name = cppArtboardNameByIndex(cppPointer, it)
            name
        }
}