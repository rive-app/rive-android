package app.rive.runtime.kotlin

import android.content.Context
import androidx.startup.Initializer
import app.rive.runtime.kotlin.core.Rive

// Initializes Rive; needs to be done at startup
//
// either call this before the view is laid out:
//
// AppInitializer.getInstance(applicationContext)
//  .initializeComponent(RiveInitializer::class.java)
//
// or declare a provider in your app's manifest:
//
//  <provider
//    android:name="androidx.startup.InitializationProvider"
//    android:authorities="${applicationId}.androidx-startup"
//    android:exported="false"
//    tools:node="merge">
//      <meta-data android:name="app.rive.runtime.kotlin.RiveInitializer"
//        android:value="androidx.startup" />
//    </provider>
//
// Include this in your dependencies:
//
//   implementation "androidx.startup:startup-runtime:1.0.0"
//
// Alternatively, you can call Rive.init() once when your app starts up and before Rive is used
//
class RiveInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        return Rive.init()
    }
    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other libraries.
        return emptyList()
    }
}