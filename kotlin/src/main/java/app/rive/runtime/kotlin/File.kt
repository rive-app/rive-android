package app.rive.runtime.kotlin

// TODO: rename to RiveFile
class File(bytes: ByteArray) {

    private val nativePointer: Long

    init {
        nativePointer = import(bytes, bytes.size)
    }

    private external fun import(bytes: ByteArray, length: Int): Long
    private external fun nativeArtboard(nativePointer: Long): Long

    fun artboard(): Artboard {
        return Artboard(
            nativeArtboard(nativePointer)
        )
    }
}