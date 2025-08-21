package app.rive.runtime.example

import android.annotation.SuppressLint
import android.os.Build
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

class TestUtils {
    companion object {
        fun waitUntil(
            atMost: Duration,
            condition: () -> Boolean
        ) {
            val maxTime = atMost.inWholeMilliseconds

            val interval: Long = 50
            var elapsed: Long = 0
            do {
                elapsed += interval
                Thread.sleep(interval)

                if (elapsed > maxTime) {
                    throw TimeoutException("Took too long.")
                }
            } while (!condition())

        }

        // Helper function to check whether it's running on an emulator
        // Found on: https://stackoverflow.com/a/21505193
        val isProbablyRunningOnEmulator: Boolean by lazy {
            // Android SDK emulator
            return@lazy ((Build.MANUFACTURER == "Google" && Build.BRAND == "google" &&
                    ((Build.FINGERPRINT.startsWith("google/sdk_gphone_")
                            && Build.FINGERPRINT.endsWith(":user/release-keys")
                            && Build.PRODUCT.startsWith("sdk_gphone_")
                            && Build.MODEL.startsWith("sdk_gphone_"))
                            //alternative
                            || (Build.FINGERPRINT.startsWith("google/sdk_gphone64_")
                            && (Build.FINGERPRINT.endsWith(":userdebug/dev-keys") || Build.FINGERPRINT.endsWith(
                        ":user/release-keys"
                    ))
                            && Build.PRODUCT.startsWith("sdk_gphone64_")
                            && Build.MODEL.startsWith("sdk_gphone64_"))))
                    //
                    || Build.FINGERPRINT.startsWith("generic")
                    || Build.FINGERPRINT.startsWith("unknown")
                    || Build.MODEL.contains("google_sdk")
                    || Build.MODEL.contains("Emulator")
                    || Build.MODEL.contains("Android SDK built for x86")
                    //bluestacks
                    || "QC_Reference_Phone" == Build.BOARD && !"Xiaomi".equals(
                Build.MANUFACTURER,
                ignoreCase = true
            )
                    //bluestacks
                    || Build.MANUFACTURER.contains("Genymotion")
                    || Build.HOST.startsWith("Build")
                    //MSI App Player
                    || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                    || Build.PRODUCT == "google_sdk"
                    // another Android SDK emulator check
                    || SystemProperties.getProp("ro.kernel.qemu") == "1")
        }
    }

    object SystemProperties {
        private var failedUsingReflection = false
        private var getPropMethod: Method? = null

        @SuppressLint("PrivateApi")
        fun getProp(propName: String, defaultResult: String = ""): String {
            if (!failedUsingReflection) try {
                if (getPropMethod == null) {
                    val clazz = Class.forName("android.os.SystemProperties")
                    getPropMethod = clazz.getMethod("get", String::class.java, String::class.java)
                }
                return getPropMethod!!.invoke(null, propName, defaultResult) as String?
                    ?: defaultResult
            } catch (_: Exception) {
                getPropMethod = null
                failedUsingReflection = true
            }
            var process: Process? = null
            try {
                process = Runtime.getRuntime().exec("getprop \"$propName\" \"$defaultResult\"")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                return reader.readLine()
            } catch (_: IOException) {
            } finally {
                process?.destroy()
            }
            return defaultResult
        }
    }
}
