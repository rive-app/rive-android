package app.rive.runtime.kotlin

// TODO: rename to RiveFile
class File(bytes: ByteArray) {

    private val nativePointer: Long

    init {
        nativePointer = import(bytes, bytes.size)
    }

    private external fun import(bytes: ByteArray, length: Int): Long
    private external fun nativeArtboard(nativePointer: Long): Long
    private external fun nativeGetArtboardByName(nativePointer: Long, name: String): Long
    private external fun nativeDelete(nativePointer: Long)

    fun artboard(): Artboard {
        var artboardPointer = nativeArtboard(nativePointer)
        if (artboardPointer == 0L) {
            throw RiveException("No Artboard found.")
        }

        return Artboard(
            artboardPointer
        )
    }

    @Throws(RiveException::class)
    fun artboard(name: String): Artboard {
        var artboardPointer = nativeGetArtboardByName(nativePointer, name)
        if (artboardPointer == 0L) {
            throw RiveException("Artboard $name not found.")
        }

        return Artboard(
            artboardPointer
        )
    }

    protected fun finalize() {
        if (nativePointer != -1L) {
            nativeDelete(nativePointer)
        }
    }
}