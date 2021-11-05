package app.rive.runtime.kotlin.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

object Rive {
    private external fun cppInitialize()
    private external fun cppCalculateRequiredBounds(
        fit: Fit, alignment: Alignment,
        availableBoundsPointer: Long,
        artboardBoundsPointer: Long,
        requiredBoundsPointer: Long
    )

    private const val JNIRiveBridge = "jnirivebridge"
    private const val JNIRiveBridgeSO = "lib${JNIRiveBridge}.so"

    private var libLoaded = false
    private var apkFile: ZipFile? = null

    private fun getApkFile(context: Context): ZipFile {
        if (apkFile != null) {
            return apkFile!!
        }

        val apkLocation = context.applicationInfo.sourceDir
        val file = File(apkLocation)
        if (!file.exists()) {
            Log.e("Rive", "No apk file for this Activity?!")
            throw IOException("Missing APK file in this context $apkLocation")
        }

        // Return and assign.
        return ZipFile(apkLocation).also { apkFile = it }
    }


    private val abiCount: Int
        get() = when {
            // Do we need this? We're already restricting to be SDK >= 21
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            -> Build.SUPPORTED_ABIS.size
            else -> 1
        }

    private fun getAbiName(index: Int): String {
        // Do we need this? We're already restricting to be SDK >= 21
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS[index]
        } else Build.CPU_ABI
    }


    /**
     * Initialises Rive.
     *
     * This loads the c++ libraries required to use Rive objects and then
     * updates the c++ environment with a pointer to the JavaVM so that
     * it can interact with Java objects.
     */
    fun init(context: Context) {
        try {
            System.loadLibrary(JNIRiveBridge)
        } catch (err: UnsatisfiedLinkError) {
            extractLib(context)
        }
        cppInitialize()
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun extractLib(context: Context) {
        var i = 0
        val maxTries = abiCount
        while (!libLoaded && i < maxTries) {
            val destDir = File(context.filesDir, "lib")
            destDir.mkdirs()

            val destLocalFile = File(destDir, "lib${JNIRiveBridge}loc.so")
            if (destLocalFile.exists()) {
                try {
                    System.load(destLocalFile.absolutePath)
                    libLoaded = true
                    return
                } catch (_: Error) {
                }
                destLocalFile.delete()
            }

            try {
                destDir.listFiles()?.forEach { it.delete() }
            } catch (_: Exception) {
            }

            val abiName = getAbiName(i)
            val abiFolder = getFolderFrom(abiName)
            try {
                if (loadFromZip(context, destLocalFile, abiFolder)) {
                    if (libLoaded) return
                }
            } catch (_: Exception) {
            }

            i++
        }

        // Don't give up.
        try {
            System.loadLibrary(JNIRiveBridge)
            libLoaded = true
        } catch (e: Error) {
            e.printStackTrace()
        }

        if (!libLoaded) {
            Log.e("Rive", "Failed to initialize JNI Rive Bridge")
        }
    }

    private fun getFolderFrom(abi: String): String {
        var folder: String
        folder = when {
            abi.equals("x86_64", true) -> {
                "x86_64"
            }
            abi.equals("arm64-v8a", true) -> {
                "arm64-v8a"
            }
            abi.equals("armeabi-v7a", true) -> {
                "armeabi-v7a"
            }
            abi.equals("armeabi", true) -> {
                "armeabi"
            }
            abi.equals("x86", true) -> {
                "x86"
            }
            abi.equals("mips", true) -> {
                "mips"
            }
            else -> {
                "armeabi"
            }
        }

        val sysArch = System.getProperty("os.arch")
        if (sysArch != null) if (sysArch.contains("686") || sysArch.contains("x86")) {
            folder = "x86"
        }
        return folder
    }


    @Throws(IOException::class)
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun loadFromZip(context: Context, destLocalFile: File, abiFolder: String): Boolean {
        val apkFile = getApkFile(context)
        val libEntryLocation = "lib/$abiFolder/$JNIRiveBridgeSO"
        val libEntry = apkFile.getEntry(libEntryLocation)
        if (libEntry == null) {
            apkFile.close()
            throw IOException("No $libEntryLocation in apkFile")
        }
        val stream = apkFile.getInputStream(libEntry)

        writeLibStream(stream, destLocalFile)
        return try {
            System.load(destLocalFile.absolutePath)
            libLoaded = true
            true
        } catch (_: Error) {
            false
        } finally {
            // Clean up resources before returning.
            stream.close()
            apkFile.close()
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun writeLibStream(inputStream: InputStream, destLocalFile: File) {
        val outputStream = FileOutputStream(destLocalFile)
        val bytesArray = ByteArray(4096)
        var read: Int
        while (inputStream.read(bytesArray).also { read = it } != -1) {
            Thread.yield()
            outputStream.write(bytesArray, 0, read)
        }
        outputStream.close()
        destLocalFile.setReadable(true, false)
        destLocalFile.setExecutable(true, false)
        destLocalFile.setWritable(true)
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