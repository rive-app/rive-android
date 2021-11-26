package app.rive.runtime.example

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.Rive


class MetricsActivity : AppCompatActivity() {
    private val containerView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<LinearLayout>(R.id.container)
    }
//
//    private fun addSwappyView() {
//        findViewById(R.id.swappy_view)
//            ?: RiveAnimationView(this, null).also {
//                val density = resources.displayMetrics.density
//                it.layoutParams = LinearLayout.LayoutParams(
//                    LinearLayout.LayoutParams.MATCH_PARENT,
//                    (366 * density).toInt(),
//                )
//                containerView.addView(it, 0)
//
//            }
//    }

//    private fun updateFps() {
//
//        val fpsView = findViewById<TextView>(R.id.fps)
//        val fps = nGetAverageFps(riveRenderer.address)
//        fpsView?.text =
//            java.lang.String.format(
//                Locale.US,
//                "Frame rate: %.1f Hz (%.2f ms)",
//                fps,
//                1e3f / fps
//            )
//    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure Rive C++ is loaded.
        Rive.init(this)
        setContentView(R.layout.activity_metrics)
        // Make sure this is initialized.
//        addSwappyView()
    }
}