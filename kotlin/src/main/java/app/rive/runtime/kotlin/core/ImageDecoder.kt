package app.rive.runtime.kotlin.core

import android.graphics.BitmapFactory

@Suppress("unused") // Called from JNI
object ImageDecoder {
    /**
     * Decodes a byte array into a bitmap and returns an integer array containing the pixel data.
     *
     * Used by C++ over JNI to decode images, specifically `RiveRenderFactory::decodeImage`.
     *
     * @param encoded The byte array containing the encoded image data.
     * @return An array of integers where the first two elements are the width and height of the
     *    bitmap, followed by the pixel data in ARGB, non-premultiplied format.
     */
    @JvmStatic
    fun decodeToBitmap(encoded: ByteArray): IntArray {
        return try {
            val bitmap =
                BitmapFactory.decodeByteArray(encoded, 0, encoded.size, BitmapFactory.Options())

            val width = bitmap.width
            val height = bitmap.height
            val offset = 2 // Space for width and height
            val pixels = IntArray(offset + width * height)
            pixels[0] = width
            pixels[1] = height
            bitmap.getPixels(pixels, offset, width, 0, 0, width, height)
            pixels
        } catch (e: Exception) {
            IntArray(0)
        }
    }
}
