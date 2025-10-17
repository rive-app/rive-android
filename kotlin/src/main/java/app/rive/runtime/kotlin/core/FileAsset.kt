package app.rive.runtime.kotlin.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.VisibleForTesting
import app.rive.runtime.kotlin.core.RiveRenderImage.Companion.fromARGBInts

sealed class FileAsset(address: Long, rendererTypeIdx: Int) : NativeObject(address) {
    private val rendererType = RendererType.fromIndex(rendererTypeIdx)
    private external fun cppName(cppPointer: Long): String
    private external fun cppUniqueFilename(cppPointer: Long): String
    private external fun cppDecode(cppPointer: Long, bytes: ByteArray, rendererType: Int): Boolean
    private external fun cppCDNUrl(cppPointer: Long): String

    val name by lazy { cppName(cppPointer) }
    val uniqueFilename by lazy { cppUniqueFilename(cppPointer) }
    val cdnUrl by lazy { cppCDNUrl(cppPointer) }

    fun decode(bytes: ByteArray): Boolean = cppDecode(cppPointer, bytes, rendererType.value)
}

/**
 * A thin Kotlin wrapper for the underlying C++ image asset. Helpful to distinguish between various
 * [FileAsset] subclasses.
 */
class ImageAsset(address: Long, rendererTypeIdx: Int) : FileAsset(address, rendererTypeIdx) {
    private external fun cppSetRenderImage(cppAsset: Long, cppRenderImage: Long)
    private external fun cppGetRenderImage(cppAsset: Long): Long
    private external fun cppImageAssetWidth(cppPointer: Long): Float
    private external fun cppImageAssetHeight(cppPointer: Long): Float

    /** The [RiveRenderImage] object associated with this [ImageAsset]. */
    var image: RiveRenderImage
        set(value) = cppSetRenderImage(cppPointer, value.cppPointer)
        /**
         * This isn't safe to use outside tests.
         *
         * @return A light wrapper around a C++ address.
         */
        @VisibleForTesting
        get() = RiveRenderImage(cppGetRenderImage(cppPointer))

    /** @return The width of the image in pixels. */
    val width: Float
        get() = cppImageAssetWidth(cppPointer)

    /** @return The height of the image in pixels. */
    val height: Float
        get() = cppImageAssetHeight(cppPointer)
}

/**
 * A thin Kotlin wrapper for the underlying C++ font asset. Helpful to distinguish between various
 * [FileAsset] subclasses.
 */
class FontAsset(address: Long, rendererTypeIdx: Int) : FileAsset(address, rendererTypeIdx) {

    private external fun cppSetFont(cppAsset: Long, cppFont: Long)
    private external fun cppGetFont(cppAsset: Long): Long

    /** The [RiveFont] object associated with this [FontAsset]. */
    var font: RiveFont
        set(value) = cppSetFont(cppPointer, value.cppPointer)
        /**
         * This isn't safe to use outside tests.
         *
         * @return A light wrapper around a C++ address.
         */
        @VisibleForTesting
        get() = RiveFont(cppGetFont(cppPointer))

}

/**
 * A thin Kotlin wrapper for the underlying C++ audio asset. Helpful to distinguish between various
 * [FileAsset] subclasses.
 */
class AudioAsset(address: Long, rendererTypeIdx: Int) : FileAsset(address, rendererTypeIdx) {

    private external fun cppSetAudio(cppAsset: Long, cppAudio: Long)
    private external fun cppGetAudio(cppAsset: Long): Long

    /** The [RiveAudio] object associated with this [AudioAsset]. */
    var audio: RiveAudio
        set(value) = cppSetAudio(cppPointer, value.cppPointer)
        /**
         * This isn't safe to use outside tests.
         *
         * @return A light wrapper around a C++ address.
         */
        @VisibleForTesting
        get() = RiveAudio(cppGetAudio(cppPointer))

}

/**
 * A wrapper around a native C++ object representing a raster image that can be rendered in Rive.
 *
 * Use the companion object methods to create one from encoded bytes, pixel bytes, ARGB integers, or
 * an [Android Bitmap][Bitmap].
 */
class RiveRenderImage internal constructor(address: Long) : NativeObject(address) {
    external override fun cppDelete(pointer: Long)

    companion object {
        private external fun cppFromRGBABytes(
            bytes: ByteArray,
            width: Int,
            height: Int,
            rendererTypeIdx: Int,
            premultiplied: Boolean
        ): Long

        private external fun cppFromARGBInts(
            colors: IntArray,
            width: Int,
            height: Int,
            rendererTypeIdx: Int,
            premultiplied: Boolean
        ): Long

        private external fun cppFromBitmapRive(bitmap: Bitmap, premultiplied: Boolean): Long
        private external fun cppFromBitmapCanvas(bitmap: Bitmap): Long

        /**
         * Creates a [RiveRenderImage] by decoding the [bytes].
         *
         * The caller is in charge of the ownership of this [NativeObject]. It must be freed by
         * calling [release].
         *
         * @param bytes Encoded bytes for the image.
         * @param rendererType The renderer for this image. This must match the renderer for the
         *    RiveAnimationView using this.
         * @return The constructed [RiveRenderImage].
         * @throws IllegalArgumentException if the bytes cannot be decoded.
         * @throws IllegalStateException if the decoded bitmap is not premultiplied.
         * @deprecated This method name is misleading; use fromEncoded instead.
         */
        @Deprecated(
            "This method name is misleading; use fromEncoded instead",
            ReplaceWith("fromEncoded(bytes, rendererType)")
        )
        fun make(
            bytes: ByteArray,
            rendererType: RendererType = Rive.defaultRendererType
        ): RiveRenderImage = fromEncoded(bytes, rendererType)

        /**
         * Creates a [RiveRenderImage] by decoding the [encodedBytes].
         *
         * The caller is in charge of the ownership of this [NativeObject]. It must be freed calling
         * [release].
         *
         * @param encodedBytes Encoded bytes for the image.
         * @param rendererType The renderer for this image. This must match the renderer for the
         *    RiveAnimationView using this.
         * @return The constructed [RiveRenderImage].
         * @throws IllegalArgumentException if the bytes cannot be decoded.
         * @throws IllegalStateException if the decoded bitmap is not premultiplied.
         */
        fun fromEncoded(
            encodedBytes: ByteArray,
            rendererType: RendererType = Rive.defaultRendererType
        ): RiveRenderImage {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPremultiplied = true
            }
            val bitmap = BitmapFactory.decodeByteArray(
                encodedBytes,
                0,
                encodedBytes.size,
                options
            )

            requireNotNull(bitmap) { "Failed to decode image from encoded bytes" }
            // Rive requires premultiplied bitmaps. If the image doesn't have alpha, it's not marked
            // as premultiplied, but the bytes are effectively premultiplied anyway with 0xFF alpha.
            check(bitmap.isPremultiplied || !bitmap.hasAlpha()) {
                "Decoded bitmap was not premultiplied"
            }

            return fromBitmap(bitmap, rendererType)
        }

        /**
         * Creates a [RiveRenderImage] from RGBA8888 pixel bytes.
         *
         * The byte array must be exactly width * height * 4 bytes.
         *
         * The caller is in charge of the ownership of this [NativeObject]. It must be freed by
         * calling [release].
         *
         * @param pixelBytes RGBA8888 pixel bytes.
         * @param width The width of the image in pixels.
         * @param height The height of the image in pixels.
         * @param rendererType The renderer for this image. This must match the renderer for the
         *    RiveAnimationView using this.
         * @param premultiplied Whether your RGB pixels are already multiplied by alpha. If false,
         *    the native layer will premultiply RGB by alpha for you. Defaults to true as Rive
         *    expects premultiplied input.
         * @return The constructed [RiveRenderImage].
         * @throws IllegalArgumentException if the pixel array is not the correct size.
         */
        fun fromRGBABytes(
            pixelBytes: ByteArray,
            width: Int,
            height: Int,
            rendererType: RendererType = Rive.defaultRendererType,
            premultiplied: Boolean = true
        ): RiveRenderImage {
            require(width > 0 && height > 0) { "Width and height must be > 0" }
            require(pixelBytes.size == width * height * 4) { "Bytes must have size = width * height * 4" }
            val address =
                cppFromRGBABytes(pixelBytes, width, height, rendererType.value, premultiplied)
            return RiveRenderImage(address)
        }

        /**
         * Creates a [RiveRenderImage] from ARGB8888 packed pixel integers, matching Android
         * [Bitmap.getPixels].
         *
         * The int array must be exactly width * height elements.
         *
         * The caller is in charge of the ownership of this [NativeObject]. It must be freed by
         * calling [release].
         *
         * @param pixels ARGB8888 packed pixel integers.
         * @param width The width of the image in pixels.
         * @param height The height of the image in pixels.
         * @param rendererType The renderer for this image. This must match the renderer for the
         *    RiveAnimationView using this.
         * @param premultiplied Whether your RGB pixels are already multiplied by alpha. If false
         *    (default), the native layer will premultiply RGB by alpha for you.
         * @return The constructed [RiveRenderImage].
         * @throws IllegalArgumentException if the pixel array is not the correct size.
         */
        fun fromARGBInts(
            pixels: IntArray,
            width: Int,
            height: Int,
            rendererType: RendererType = Rive.defaultRendererType,
            premultiplied: Boolean = false
        ): RiveRenderImage {
            require(width > 0 && height > 0) { "Width and height must be > 0" }
            require(pixels.size == width * height) { "Colors must have size = width * height" }

            // We can shortcut Bitmap JNI back and forth if using the Canvas renderer and the
            // pixels are not premultiplied.
            if (rendererType == RendererType.Canvas && !premultiplied) {
                val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                bitmap.isPremultiplied = true
                val address = cppFromBitmapCanvas(bitmap)
                return RiveRenderImage(address)
            }

            val address = cppFromARGBInts(pixels, width, height, rendererType.value, premultiplied)
            return RiveRenderImage(address)
        }

        /**
         * Create a [RiveRenderImage] from a [Bitmap]. Ensures ARGB_8888 software bitmap, copying if
         * not, and forwards to [fromARGBInts].
         *
         * The caller is in charge of the ownership of this [NativeObject]. It must be freed by
         * calling [release].
         *
         * The bitmap supplied is given directly to the canvas when using the Canvas renderer, so
         * ensure that it is not recycled while in use by Rive.
         *
         * @param bitmap The source bitmap. Must not be recycled.
         * @param rendererType The renderer for this image. This must match the renderer for the
         *    RiveAnimationView using this.
         * @return The constructed [RiveRenderImage].
         * @throws IllegalArgumentException if the bitmap is recycled.
         * @throws IllegalStateException if the bitmap could not be copied to ARGB_8888.
         */
        fun fromBitmap(
            bitmap: Bitmap,
            rendererType: RendererType = Rive.defaultRendererType,
        ): RiveRenderImage {
            require(!bitmap.isRecycled) { "Bitmap must not be recycled" }
            val isHardware =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE
            val safeBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888 && !isHardware) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    ?: error("Failed to copy bitmap to ARGB_8888")
            }

            return if (rendererType == RendererType.Rive) {
                val address = cppFromBitmapRive(safeBitmap, safeBitmap.isPremultiplied)
                RiveRenderImage(address)
            } else {
                val address = cppFromBitmapCanvas(safeBitmap)
                RiveRenderImage(address)
            }
        }
    }
}

/**
 * A wrapper around a native C++ object representing a font that can be rendered in Rive.
 *
 * Use the companion object method to create one from encoded bytes.
 */
class RiveFont internal constructor(address: Long) : NativeObject(address) {
    external override fun cppDelete(pointer: Long)

    companion object {
        private external fun cppMakeFont(bytes: ByteArray, rendererTypeIdx: Int): Long

        /**
         * Creates a [RiveFont] by decoding [bytes].
         *
         * The caller is in charge of the ownership of this [NativeObject]. It must be freed by
         * calling [release].
         *
         * @param bytes Encoded bytes for a font.
         * @param rendererType The renderer decoding this font. This needs to match the renderer for
         *    the View using this.
         * @return The constructed [RiveFont] object.
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

/**
 * A wrapper around a native C++ object representing an audio clip that can be played in Rive.
 *
 * Use the companion object method to create one from encoded bytes.
 */
class RiveAudio internal constructor(address: Long) : NativeObject(address) {
    external override fun cppDelete(pointer: Long)

    companion object {
        private external fun cppMakeAudio(bytes: ByteArray, rendererTypeIdx: Int): Long

        /**
         * Creates a [RiveAudio] by decoding [bytes].
         *
         * The caller is in charge of the ownership of this [NativeObject]. It must be freed by
         * calling [release].
         *
         * @param bytes Encoded bytes for an audio file.
         * @param rendererType The renderer decoding this object. This needs to match the renderer
         *    for the View using this.
         * @return The constructed [RiveAudio] object.
         */
        fun make(
            bytes: ByteArray,
            rendererType: RendererType = Rive.defaultRendererType
        ): RiveAudio {
            val address = cppMakeAudio(bytes, rendererType.value)
            return RiveAudio(address)
        }
    }
}
