package app.rive.core

import app.rive.RiveInitializationException
import app.rive.RiveLog
import java.io.File
import java.nio.file.Files

/**
 * Locates the desktop Rive native libraries (`librive-jvm.dylib` + bundled MoltenVK).
 *
 * Used by the desktop target's loader and by Android Studio previews (layoutlib runs the
 * Android classpath on a host JVM, where the Android `.so` cannot load).
 *
 * The libraries are bundled as classpath resources under `rive-native/<os>-<arch>/` (in the
 * desktop JAR and in the `rive-preview` artifact) and extracted to a fresh temporary directory
 * per classloader, which keeps repeated loads in separate classloaders (e.g. preview refreshes)
 * from tripping the JVM's "already loaded in another classloader" restriction.
 *
 * Override the location with `-Drive.native.path=/dir/containing/dylibs` for local development.
 */
internal object DesktopNatives {
    private const val TAG = "RiveNative"

    /** @return A directory containing the desktop native libraries. */
    fun nativeDir(): File = explicitNativeDir() ?: extractBundledNatives()

    private fun explicitNativeDir(): File? {
        val path = System.getProperty("rive.native.path") ?: return null
        val dir = File(path)
        if (!dir.isDirectory) {
            throw RiveInitializationException("rive.native.path is not a directory: $path")
        }
        return dir
    }

    private fun extractBundledNatives(): File {
        val osArch = System.getProperty("os.arch").let {
            if (it == "aarch64" || it == "arm64") "aarch64" else "x86_64"
        }
        val resourceDir = "rive-native/macos-$osArch"
        val tempDir = Files.createTempDirectory("rive-native").toFile().apply { deleteOnExit() }
        for (name in listOf("librive-jvm.dylib", "libMoltenVK.dylib")) {
            val resource = DesktopNatives::class.java.classLoader
                .getResourceAsStream("$resourceDir/$name")
            if (resource == null) {
                if (name == "libMoltenVK.dylib") continue // optional; system Vulkan may exist
                throw RiveInitializationException(
                    "Native resource $resourceDir/$name not found on the classpath. " +
                            "Add the app.rive:rive-preview dependency (previews) or set " +
                            "-Drive.native.path."
                )
            }
            resource.use { input ->
                File(tempDir, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
        RiveLog.d(TAG) { "Extracted Rive natives to ${tempDir.absolutePath}" }
        return tempDir
    }
}
