package app.rive

import android.content.Context
import androidx.startup.Initializer
import app.rive.core.RiveNative

/**
 * Eagerly initializes Rive at app startup.
 *
 * Rive initializes itself lazily when the first worker is created, so using this initializer is
 * optional. To front-load the native library load, either call this before the first Rive
 * composable is displayed:
 * ```kotlin
 * AppInitializer.getInstance(applicationContext)
 *  .initializeComponent(RiveInitializer::class.java)
 * ```
 *
 * or declare a provider in your app's manifest:
 * ```xml
 * <provider
 *    android:name="androidx.startup.InitializationProvider"
 *    android:authorities="${applicationId}.androidx-startup"
 *    android:exported="false"
 *    tools:node="merge">
 *      <meta-data android:name="app.rive.RiveInitializer"
 *        android:value="androidx.startup" />
 *  </provider>
 * ```
 *
 * Include this in your dependencies:
 * ```gradle
 * implementation "androidx.startup:startup-runtime:1.0.0"
 * ```
 */
class RiveInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        RiveNative.ensureLoaded()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other libraries.
        return emptyList()
    }
}
