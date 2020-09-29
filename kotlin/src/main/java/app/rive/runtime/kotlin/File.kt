package app.rive.runtime.kotlin


class File {
    private var nativePointer: Long
    external private fun import(bytes: ByteArray, length: Int): Long
    external private fun nativeArtboard(nativePointer: Long): Long

    companion object {
        init {
            System.loadLibrary("jnirivebridge")
        }
    }

    constructor(bytes: ByteArray) : super() {
        nativePointer = import(
            bytes,
            bytes.size
        )
    }

    fun artboard(): Artboard {
        return Artboard(
            nativeArtboard(nativePointer)
        )
    }
}