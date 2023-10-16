package app.rive.runtime.kotlin.core

class FileAsset(address: Long, rendererTypeIdx: Int) : NativeObject(address) {
    private val rendererType = RendererType.fromIndex(rendererTypeIdx)
    private external fun cppName(cppPointer: Long): String
    private external fun cppUniqueFilename(cppPointer: Long): String
    private external fun cppDecode(cppPointer: Long, bytes: ByteArray, rendererType: Int): Boolean
    private external fun cppCDNUrl(cppPointer: Long): String

    val name by lazy { cppName(cppPointer) }
    val uniqueFilename by lazy { cppUniqueFilename(cppPointer) }
    val cdnUrl by lazy { cppCDNUrl(cppPointer) }

    fun decode(bytes: ByteArray): Boolean {
        return cppDecode(cppPointer, bytes, rendererType.value)
    }
}
