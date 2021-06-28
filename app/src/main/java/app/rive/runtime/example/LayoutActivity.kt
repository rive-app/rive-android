package app.rive.runtime.example

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit

class LayoutActivity : AppCompatActivity() {

    val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.layout_view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)

        findViewById<Button>(R.id.fit_fill).setOnClickListener {
            animationView.fit = Fit.FILL
        }
        findViewById<Button>(R.id.fit_contain).setOnClickListener {
            animationView.fit = Fit.CONTAIN
        }
        findViewById<Button>(R.id.fit_cover).setOnClickListener {
            animationView.fit = Fit.COVER
        }
        findViewById<Button>(R.id.fit_fit_width).setOnClickListener {
            animationView.fit = Fit.FIT_WIDTH
        }
        findViewById<Button>(R.id.fit_fit_height).setOnClickListener {
            animationView.fit = Fit.FIT_HEIGHT
        }
        findViewById<Button>(R.id.fit_none).setOnClickListener {
            animationView.fit = Fit.NONE
        }
        findViewById<Button>(R.id.fit_scale_down).setOnClickListener {
            animationView.fit = Fit.SCALE_DOWN
        }


        findViewById<Button>(R.id.alignment_top_left).setOnClickListener {
            animationView.alignment = Alignment.TOP_LEFT
        }
        findViewById<Button>(R.id.alignment_top_center).setOnClickListener {
            animationView.alignment = Alignment.TOP_CENTER
        }
        findViewById<Button>(R.id.alignment_top_right).setOnClickListener {
            animationView.alignment = Alignment.TOP_RIGHT
        }

        findViewById<Button>(R.id.alignment_center_left).setOnClickListener {
            animationView.alignment = Alignment.CENTER_LEFT
        }
        findViewById<Button>(R.id.alignment_center).setOnClickListener {
            animationView.alignment = Alignment.CENTER
        }
        findViewById<Button>(R.id.alignment_center_right).setOnClickListener {
            animationView.alignment = Alignment.CENTER_RIGHT
        }

        findViewById<Button>(R.id.alignment_bottom_left).setOnClickListener {
            animationView.alignment = Alignment.BOTTOM_LEFT
        }
        findViewById<Button>(R.id.alignment_bottom_center).setOnClickListener {
            animationView.alignment = Alignment.BOTTOM_CENTER
        }
        findViewById<Button>(R.id.alignment_bottom_right).setOnClickListener {
            animationView.alignment = Alignment.BOTTOM_RIGHT
        }



    }


    override fun onDestroy() {
        super.onDestroy()
        animationView.destroy()
    }
}
