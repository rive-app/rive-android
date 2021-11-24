package app.rive.runtime.example

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive


class MetricsActivity : AppCompatActivity() {
    private val containerView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<LinearLayout>(R.id.container)
    }

    private fun addSwappyView() {
        findViewById(R.id.swappy_view)
            ?: RiveAnimationView(this, null).also {
                val density = resources.displayMetrics.density
                it.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (366 * density).toInt(),
                )
                containerView.addView(it, 0)
            }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure Rive C++ is loaded.
        Rive.init(this)
        setContentView(R.layout.activity_metrics)
        // Make sure this is initialized.
        addSwappyView()
    }
}