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
    fun nativeDir(): File {
        val stableSource = explicitNativeDir() ?: generatedPathFallback()
        // The JVM refuses to load the same library *file* into two classloaders, and preview
        // refreshes create fresh classloaders. A fresh per-call copy has a distinct path, which
        // is what the JVM keys native-library identity on.
        return stableSource?.let(::copyToFreshTempDir) ?: extractBundledNatives()
    }

    private fun copyToFreshTempDir(source: File): File {
        val tempDir = Files.createTempDirectory("rive-native").toFile().apply { deleteOnExit() }
        for (name in listOf("librive-jvm.dylib", "libMoltenVK.dylib")) {
            val file = File(source, name)
            if (file.isFile) {
                file.copyTo(File(tempDir, name))
            }
        }
        RiveLog.d(TAG) { "Copied Rive natives from ${source.absolutePath} to ${tempDir.absolutePath}" }
        return tempDir
    }

    /**
     * Resolves the staged-natives path baked into `app.rive.preview.RiveNativePaths` by the
     * rive-preview module. Android Studio's preview classloader refuses classpath resource
     * lookups but loads classes normally, so a generated class is the only dependable channel
     * for previews.
     */
    private fun generatedPathFallback(): File? = runCatching {
        val pathsClass = Class.forName(
            "app.rive.preview.RiveNativePaths",
            false,
            DesktopNatives::class.java.classLoader
        )
        val dir = File(pathsClass.getField("NATIVE_DIR").get(null) as String)
        dir.takeIf { File(it, "librive-jvm.dylib").isFile }?.also {
            RiveLog.d(TAG) { "Using rive-preview staged natives at ${it.absolutePath}" }
        }
    }.getOrNull()

    private fun explicitNativeDir(): File? {
        val path = System.getProperty("rive.native.path") ?: return null
        val dir = File(path)
        if (!dir.isDirectory) {
            throw RiveInitializationException("rive.native.path is not a directory: $path")
        }
        return dir
    }

    /**
     * Opens a classpath resource, trying each plausible classloader. Sandboxed environments
     * (layoutlib's ModuleClassLoader in Android Studio previews) restrict what individual
     * loaders can see, so no single loader works everywhere.
     */
    private fun openResource(path: String): java.io.InputStream? {
        val candidates = listOfNotNull(
            DesktopNatives::class.java.classLoader,
            Thread.currentThread().contextClassLoader,
            ClassLoader.getSystemClassLoader(),
        )
        for (loader in candidates) {
            loader.getResourceAsStream(path)?.let { return it }
        }
        return null
    }

    /**
     * Locates the rive-preview staging directory by walking up from where this class was
     * loaded. Preview classloaders may refuse resource lookups entirely; for project-local
     * builds (this repo's own previews) the staged natives sit at a known path relative to
     * the build tree.
     */
    private fun projectLayoutFallback(resourceDir: String): File? {
        val location = runCatching {
            DesktopNatives::class.java.protectionDomain?.codeSource?.location?.toURI()
        }.getOrNull() ?: return null
        var dir: File? = File(location)
        repeat(12) {
            dir = dir?.parentFile ?: return null
            val candidate = File(dir, "rive-preview/build/generated/riveNatives/$resourceDir")
            if (File(candidate, "librive-jvm.dylib").isFile) return candidate
        }
        return null
    }

    private fun extractBundledNatives(): File {
        val osArch = System.getProperty("os.arch").let {
            if (it == "aarch64" || it == "arm64") "aarch64" else "x86_64"
        }
        val resourceDir = "rive-native/macos-$osArch"
        val tempDir = Files.createTempDirectory("rive-native").toFile().apply { deleteOnExit() }
        for (name in listOf("librive-jvm.dylib", "libMoltenVK.dylib")) {
            val resource = openResource("$resourceDir/$name")
            if (resource == null) {
                if (name == "libMoltenVK.dylib") continue // optional; system Vulkan may exist
                projectLayoutFallback(resourceDir)?.let { return it }
                throw RiveInitializationException(
                    "Native resource $resourceDir/$name not found on the classpath. " +
                            "Add the app.rive:rive-preview dependency (previews) or set " +
                            "-Drive.native.path. Diagnostics: ${classpathDiagnostics()}"
                )
            }
            resource.use { input ->
                File(tempDir, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
        RiveLog.d(TAG) { "Extracted Rive natives to ${tempDir.absolutePath}" }
        return tempDir
    }

    private fun classpathDiagnostics(): String {
        val codeSource = runCatching {
            DesktopNatives::class.java.protectionDomain?.codeSource?.location?.toString()
        }.getOrElse { "error: $it" }
        val loaders = listOfNotNull(
            "class" to DesktopNatives::class.java.classLoader,
            Thread.currentThread().contextClassLoader?.let { "context" to it },
            "system" to ClassLoader.getSystemClassLoader(),
        ).joinToString("; ") { (label, loader) ->
            val self = loader.getResource("app/rive/core/DesktopNatives.class") != null
            val res = loader.getResource("rive-native") != null
            "$label=${loader.javaClass.simpleName}(seesSelf=$self, seesRiveNative=$res)"
        }
        return "codeSource=$codeSource; $loaders"
    }
}
