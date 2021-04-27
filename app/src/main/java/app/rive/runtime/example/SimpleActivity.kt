package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive

class SimpleActivity : AppCompatActivity() {

    private val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.simple_view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple)
    }

    override fun onDestroy() {
        super.onDestroy()
        animationView.destroy()
    }
}
