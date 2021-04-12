package app.rive.runtime.kotlin

import java.lang.Exception

// TODO: rename to RiveFile
class File(bytes: ByteArray) {

    private val nativePointer: Long

    init {
        nativePointer = import(bytes, bytes.size)
    }

    private external fun import(bytes: ByteArray, length: Int): Long
    private external fun nativeArtboard(nativePointer: Long): Long
    private external fun nativeDelete(nativePointer: Long)

    fun artboard(): Artboard {
        return Artboard(
            nativeArtboard(nativePointer)
        )
    }
    protected fun finalize() {
        if (nativePointer != -1L) {
            nativeDelete(nativePointer)
        }
    }
}