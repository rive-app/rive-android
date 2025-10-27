package app.rive.runtime.kotlin

import android.content.Context
import androidx.startup.Initializer
import app.rive.runtime.kotlin.core.Rive

/**
 * Initializes Rive; needs to be done at startup.
 *
 * Either call this before the view is laid out:
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
 *      <meta-data android:name="app.rive.runtime.kotlin.RiveInitializer"
 *        android:value="androidx.startup" />
 *  </provider>
 * ```
 *
 * Include this in your dependencies:
 * ```gradle
 * implementation "androidx.startup:startup-runtime:1.0.0"
 * ```
 *
 * Alternatively, you can call Rive.init(context) once when your app starts up and before Rive is
 * used. In fact, if you want to provide a custom renderer type you'll need to init Rive manually.
 */

class RiveInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        return Rive.init(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other libraries.
        return emptyList()
    }
}
