package app.rive.core

import app.rive.RiveInitializationException
import app.rive.RiveLog
import java.io.File

/**
 * Loads the desktop Rive native library (`librive-jvm`) and initializes the C++ environment.
 *
 * See [DesktopNatives] for how the library and its MoltenVK dependency are located.
 */
object RiveNative {
    private const val TAG = "RiveNative"

    @Volatile
    private var loaded = false

    /**
     * Loads the native library and initializes the C++ environment. Idempotent and thread-safe.
     *
     * @throws RiveInitializationException if the native library cannot be found or loaded.
     */
    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val dir = DesktopNatives.nativeDir()
            val riveJvm = File(dir, "librive-jvm.dylib")
            if (!riveJvm.isFile) {
                throw RiveInitializationException(
                    "librive-jvm.dylib not found in ${dir.absolutePath}"
                )
            }
            try {
                System.load(riveJvm.absolutePath)
            } catch (error: UnsatisfiedLinkError) {
                throw RiveInitializationException(
                    "Failed to load ${riveJvm.absolutePath}",
                    error
                )
            }
            val vulkan = File(dir, "libMoltenVK.dylib")
            if (vulkan.isFile) {
                RiveLog.d(TAG) { "Using bundled MoltenVK at ${vulkan.absolutePath}" }
                cppSetVulkanLibraryPath(vulkan.absolutePath)
            }
            cppInitialize()
            loaded = true
        }
    }

    /** Initializes the JNI bindings (JVM pointer, class loader anchor, logging). */
    private external fun cppInitialize()

    /** Points the Vulkan bootstrap at the extracted MoltenVK. */
    private external fun cppSetVulkanLibraryPath(path: String)
}
