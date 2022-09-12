package app.rive.runtime.kotlin.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class Decoder {
    // output array caries width,height in the first two slots:
    //  output[0] is width
    //  output[1] is height
    //  output[2...] are the pixel values
    //
    static int[] decodeToPixels(byte[] encoded) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inMutable = true;   // since we want to read its pixels
            opts.inScaled = false;   // we handle scaling at draw-time
            Bitmap bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.length, opts);

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int offset = 2; // [0] is width, [1] is height
            int[] pixels = new int[offset + width * height];
            bitmap.getPixels(pixels, offset, width, 0, 0, width, height);

            pixels[0] = width;
            pixels[1] = height;
            return pixels;  // bgra unpremul with stride == width
        } catch (Exception e) {
            // fall out
        }
        return new int[0]; // failed to decode
    }
}
