package app.rive.runtime.compatibilitytest

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

// TODO: 
// I'd like to trigger this after we finished rendering, is it possible to do that? in the meantime we are simply time based. 
class BitMapExtractor {

    fun extractView(
        activity: AppCompatActivity,
        view: RiveAnimationView,
        name: String,
        delayMillis: Long = 2000
    ) {

        Handler(Looper.getMainLooper()).postDelayed({

            val bitmap: Bitmap? = view.bitmap

            val fos: OutputStream?

            // (Permission denied) has come in, might need to do some more content
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = activity.contentResolver
                val contentValues = ContentValues()
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                contentValues.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES
                )
                val imageUri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = resolver.openOutputStream(imageUri!!)
            } else {
                val imagesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                val image = File(imagesDir, "$name.png")
                fos = FileOutputStream(image)
            }

            bitmap?.compress(
                Bitmap.CompressFormat.PNG,
                100,
                fos!!
            )
            fos!!.close()
            Log.d("BitmapExtractor", "Written image to $name")
        }, delayMillis)
    }
}

val extractor = BitMapExtractor()