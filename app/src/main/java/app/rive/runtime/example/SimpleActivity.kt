package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive

class SimpleActivity : AppCompatActivity() {

    private val animationViewAsset by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.simple_view_asset)
    }

//    private val animationViewNetwork by lazy(LazyThreadSafetyMode.NONE) {
//        findViewById<RiveAnimationView>(R.id.simple_view_network)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple)
    }
}
