package app.rive.runtime.kotlin.core

import androidx.annotation.VisibleForTesting

sealed class FileAsset(address: Long, rendererTypeIdx: Int) : NativeObject(address) {
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


/**
 * A thin Kotlin wrapper for the underlying C++ [ImageAsset].
 * Helpful to distinguish between various [FileAsset] subclasses.
 */
class ImageAsset(address: Long, rendererTypeIdx: Int) : FileAsset(address, rendererTypeIdx) {
    private external fun cppSetRenderImage(cppImageAsset: Long, cppRenderImage: Long)
    private external fun cppGetRenderImage(cppImageAsset: Long): Long

    /**
     * The [RiveRenderImage] object associated with this [ImageAsset].
     */
    var image: RiveRenderImage
        set(value) {
            cppSetRenderImage(cppPointer, value.cppPointer)
        }
        /**
         * This isn't safe to use outside tests.
         *
         * @return a light wrapper for a C++ address.
         */
        @VisibleForTesting
        get() = RiveRenderImage(cppGetRenderImage(cppPointer))

}

/**
 * A thin Kotlin wrapper for the underlying C++ [FontAsset].
 * Helpful to distinguish between various [FileAsset] subclasses.
 */
class FontAsset(address: Long, rendererTypeIdx: Int) : FileAsset(address, rendererTypeIdx) {

    private external fun cppSetFont(cppImageAsset: Long, cppFont: Long)
    private external fun cppGetFont(cppImageAsset: Long): Long

    /**
     * The [RiveFont] object associated with this [FontAsset].
     */
    var font: RiveFont
        set(value) {
            cppSetFont(cppPointer, value.cppPointer)
        }
        /**
         * This isn't safe to use outside tests.
         *
         * @return a light wrapper for a C++ address.
         */
        @VisibleForTesting
        get() = RiveFont(cppGetFont(cppPointer))

}

/**
 * A native object representing a Rive RenderImage.
 */
class RiveRenderImage internal constructor(address: Long) : NativeObject(address) {
    external override fun cppDelete(pointer: Long)

    companion object {
        private external fun cppMakeImage(bytes: ByteArray, rendererTypeIdx: Int): Long

        /**
         * It creates a [RiveRenderImage] for the caller decoding [bytes].
         * The caller is in charge of the ownership of this [NativeObject]. It can be freed calling
         * [release]
         *
         * @param bytes encoded bytes for an image.
         * @param rendererType the renderer for this image. This needs to match the renderer for
         *  the View using this.
         * @return
         */
        fun make(
            bytes: ByteArray,
            rendererType: RendererType = Rive.defaultRendererType
        ): RiveRenderImage {
            val address = cppMakeImage(bytes, rendererType.value)
            return RiveRenderImage(address)
        }
    }
}

/**
 * A native object representing a Rive Font.
 */
class RiveFont internal constructor(address: Long) : NativeObject(address) {
    external override fun cppDelete(pointer: Long)

    companion object {
        private external fun cppMakeFont(bytes: ByteArray, rendererTypeIdx: Int): Long

        /**
         * It creates a [RiveFont] for the caller decoding [bytes].
         * The caller is in charge of the ownership of this [NativeObject]. It can be freed calling
         * [release]
         *
         * @param bytes encoded bytes for an image.
         * @param rendererType the renderer decoding this font. This needs to match the renderer for
         *  the View using this.
         * @return
         */
        fun make(
            bytes: ByteArray,
            rendererType: RendererType = Rive.defaultRendererType
        ): RiveFont {
            val address = cppMakeFont(bytes, rendererType.value)
            return RiveFont(address)
        }
    }
}