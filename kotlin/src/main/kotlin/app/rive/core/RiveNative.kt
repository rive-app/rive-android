package app.rive.core

import android.os.Build
import android.os.Process
import app.rive.RiveLog

/**
 * Loads the Rive native library and initializes the C++ environment.
 *
 * Loading happens automatically the first time a [CommandQueue] is created. To load eagerly at
 * app startup instead, either call [ensureLoaded] yourself or declare the
 * [app.rive.RiveInitializer] provider in your app's manifest.
 *
 * If your app loads native libraries manually (for example with split APKs / dynamic feature
 * delivery), load `libc++_shared.so` and `librive-android.so` yourself and then call
 * [initializeCppEnvironment]:
 * ```
 * System.loadLibrary("c++_shared")
 * System.loadLibrary("rive-android")
 * // or from a split context:
 * SplitInstallHelper.loadLibrary(context, "c++_shared")
 * SplitInstallHelper.loadLibrary(context, "rive-android")
 * RiveNative.initializeCppEnvironment()
 * ```
 */
object RiveNative {
    private const val TAG = "RiveNative"
    private const val RIVE_ANDROID = "rive-android"

    @Volatile
    private var loaded = false

    /**
     * Loads the native library and initializes the C++ environment. Idempotent and thread-safe.
     *
     * @throws UnsatisfiedLinkError if the native library cannot be loaded.
     */
    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                System.loadLibrary(RIVE_ANDROID)
            } catch (error: UnsatisfiedLinkError) {
                logLoadFailure(error)
                throw error
            }
            initializeCppEnvironment()
            loaded = true
        }
    }

    /**
     * Initializes the JNI bindings.
     *
     * We update the C++ environment with a pointer to the JavaVM so that it can interact with JVM
     * objects.
     *
     * Normally done as part of [ensureLoaded], and only required if you are loading the native
     * libraries yourself.
     */
    @JvmStatic
    fun initializeCppEnvironment() = cppInitialize()

    private fun logLoadFailure(error: UnsatisfiedLinkError) {
        val supportedABIs = Build.SUPPORTED_ABIS.joinToString(prefix = "[", postfix = "]")
        val is64BitDevice = Process.is64Bit()
        RiveLog.e(TAG, error) {
            "Failed to load lib$RIVE_ANDROID.so. " +
                    "Supported ABIs: $supportedABIs. " +
                    "Device bitness: ${if (is64BitDevice) "64-bit" else "32-bit"}. " +
                    "Check your APK/AAB contains lib/<abi>/lib$RIVE_ANDROID.so and verify ABI " +
                    "filters, split APK/dynamic feature delivery, and 32-bit support " +
                    "(for example armeabi-v7a) are not stripped."
        }
    }

    private external fun cppInitialize()
}
