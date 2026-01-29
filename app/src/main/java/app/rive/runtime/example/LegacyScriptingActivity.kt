package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import app.rive.runtime.kotlin.RiveAnimationView
import android.graphics.Color as AndroidColor

class LegacyScriptingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        val riveView = RiveAnimationView.Builder(this)
            .setResource(R.raw.blinko)
            .setAutoBind(true)
            .build()
        setContentView(riveView)
    }
}
