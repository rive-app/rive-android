package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView

class BlendActivity : AppCompatActivity() {

    private val animationViewAsset by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.blend)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blend)
    }
}
