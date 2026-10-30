package app.rive.runtime.kotlin.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.os.Build

@Suppress("unused") // Called from JNI
object ImageDecoder {
    /**
     * Decodes encoded image bytes into a premultiplied sRGB RGBA_8888 bitmap.
     *
     * Used by C++ over JNI to decode images, specifically `RiveRenderFactory::decodeImage`, which
     * copies the pixels out and recycles the bitmap. Decoding straight to sRGB keeps the
     * conversion inside the codec instead of a second pass over the pixels.
     *
     * @param encoded The byte array containing the encoded image data.
     * @return The bitmap, or null if decoding failed.
     */
    @JvmStatic
    fun decodeToRGBA(encoded: ByteArray): Bitmap? = try {
        val bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, decodeOptions())
        // Some sources, such as 16 bit PNGs, can still decode to another config and color space.
        // Drawing into a new sRGB bitmap converts both, which copy() would not.
        if (bitmap == null || bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()
            }
        }
    } catch (e: Exception) {
        null
    }

    internal fun decodeOptions() = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
    }
}
