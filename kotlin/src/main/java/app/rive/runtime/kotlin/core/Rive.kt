package app.rive.runtime.kotlin.core

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.*
import java.io.File
import java.util.zip.ZipFile

object Rive {
    private external fun cppInitialize()
    private external fun cppCalculateRequiredBounds(
        fit: Fit, alignment: Alignment,
        availableBoundsPointer: Long,
        artboardBoundsPointer: Long,
        requiredBoundsPointer: Long
    )

    private const val JNIRiveBirdge = "jnirivebridge"

    /**
     * Initialises Rive.
     *
     * This loads the c++ libraries required to use Rive objects and then
     * updates the c++ environment with a pointer to the JavaVM so that
     * it can interact with Java objects.
     */
    fun init(context: Context) {
        try {
            System.loadLibrary(JNIRiveBirdge)
        } catch (err: UnsatisfiedLinkError) {
            extractLib(context)
        }
        cppInitialize()
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun extractFile(inputStream: InputStream, destPath: String) {
        val outStream = BufferedOutputStream(FileOutputStream(destPath))
        val bytesArray = ByteArray(4096)
        var read: Int
        while (inputStream.read(bytesArray).also { read = it } != -1) {
            outStream.write(bytesArray, 0, read)
        }
        outStream.close()
        System.load(destPath)
    }

    private fun extractLib(context: Context) {
        val apkLocation = context.applicationInfo.sourceDir
        val apkFile = File(apkLocation)
        if (!apkFile.exists()) {
            Log.e("Rive", "No apk file for this activity?!")
            throw IOException("Couldn't find the apk file?!")
        }
        val apkZip = ZipFile(apkLocation)
        apkZip.use { archive ->
            archive.entries().asSequence().first { entry ->
                entry.name.contains(JNIRiveBirdge)
            }.let { lib ->
                val libStream = archive.getInputStream(lib)
                val dataDir = context.applicationInfo.dataDir
                val libName = lib.name
                val filename = libName.substring(libName.lastIndexOf('/') + 1)
                extractFile(libStream, "$dataDir/$filename")
            }
        }
    }


    fun calculateRequiredBounds(
        fit: Fit,
        alignment: Alignment,
        availableBounds: AABB,
        artboardBounds: AABB
    ): AABB {
        val requiredBounds = AABB(0f, 0f)
        cppCalculateRequiredBounds(
            fit,
            alignment,
            availableBounds.cppPointer,
            artboardBounds.cppPointer,
            requiredBounds.cppPointer
        )
        return requiredBounds
    }

}