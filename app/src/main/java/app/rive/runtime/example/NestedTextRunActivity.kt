package app.rive.runtime.example

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.ComponentActivity
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import app.rive.runtime.kotlin.RiveAnimationView

class NestedTextRunActivity : ComponentActivity() {

    private val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.nested_text_run)
    }

    fun onButtonClick(view: View) {
        if (view is Button) {
            when (view.id) {
                R.id.set_b1 -> {
                    Log.d(
                        "nested-text-run",
                        animationView.getTextRunValue("ArtboardBRun", "ArtboardB-1").toString()
                    )
                    animationView.setTextRunValue(
                        textRunName = "ArtboardBRun",
                        textValue = "ArtboardB-1 Updated",
                        path = "ArtboardB-1"
                    )
                    Log.d(
                        "nested-text-run",
                        animationView.getTextRunValue("ArtboardBRun", "ArtboardB-1").toString()
                    )
                }

                R.id.set_b2 -> {
                    animationView.setTextRunValue(
                        "ArtboardBRun",
                        "ArtboardB-2 Updated",
                        "ArtboardB-2"
                    )
                }

                R.id.set_b1_c1 -> {
                    animationView.setTextRunValue(
                        "ArtboardCRun",
                        "ArtboardB-1/C-1 Updated",
                        "ArtboardB-1/ArtboardC-1"
                    )
                }

                R.id.set_b1_c2 -> {
                    animationView.setTextRunValue(
                        "ArtboardCRun",
                        "ArtboardB-1/C-2 Updated",
                        "ArtboardB-1/ArtboardC-2"
                    )
                }

                R.id.set_b2_c1 -> {
                    animationView.setTextRunValue(
                        "ArtboardCRun",
                        "ArtboardB-2/C-1 Updated",
                        "ArtboardB-2/ArtboardC-1"
                    )
                }

                R.id.set_b2_c2 -> {
                    animationView.setTextRunValue(
                        "ArtboardCRun",
                        "ArtboardB-2/C-2 Updated",
                        "ArtboardB-2/ArtboardC-2"
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeContent(R.layout.nested_text_run)
    }
}
