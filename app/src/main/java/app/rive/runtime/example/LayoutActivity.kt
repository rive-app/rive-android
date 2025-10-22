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

        animationView.setRiveResource(R.raw.responsive_layouts, autoBind = true)
        animationView.fit = Fit.LAYOUT

        // FIT BUTTONS

        findViewById<Button>(R.id.fit_layout).setOnClickListener {
            animationView.fit = Fit.LAYOUT
            animationView.play()
        }
        findViewById<Button>(R.id.fit_fill).setOnClickListener {
            animationView.fit = Fit.FILL
            animationView.play()
        }
        findViewById<Button>(R.id.fit_contain).setOnClickListener {
            animationView.fit = Fit.CONTAIN
            animationView.play()
        }
        findViewById<Button>(R.id.fit_cover).setOnClickListener {
            animationView.fit = Fit.COVER
            animationView.play()
        }
        findViewById<Button>(R.id.fit_fit_width).setOnClickListener {
            animationView.fit = Fit.FIT_WIDTH
            animationView.play()
        }
        findViewById<Button>(R.id.fit_fit_height).setOnClickListener {
            animationView.fit = Fit.FIT_HEIGHT
            animationView.play()
        }
        findViewById<Button>(R.id.fit_none).setOnClickListener {
            animationView.fit = Fit.NONE
            animationView.play()
        }
        findViewById<Button>(R.id.fit_scale_down).setOnClickListener {
            animationView.fit = Fit.SCALE_DOWN
            animationView.play()
        }

        // SCALE BUTTONS
        val defaultScaleFactor = animationView.layoutScaleFactorAutomatic / 1.5f
        val increment = 0.1f

        animationView.layoutScaleFactor = defaultScaleFactor;
        findViewById<Button>(R.id.scale_up).setOnClickListener {
            if (animationView.fit != Fit.LAYOUT) {
                return@setOnClickListener;
            }

            if (animationView.layoutScaleFactor == null) {
                // Auto layout scale factor is enabled
                // Reset the view to the current auto value
                animationView.layoutScaleFactor = defaultScaleFactor
            }

            animationView.layoutScaleFactor = animationView.layoutScaleFactor?.plus(increment);
            animationView.play()
        }
        findViewById<Button>(R.id.scale_down).setOnClickListener {
            if (animationView.fit != Fit.LAYOUT) {
                return@setOnClickListener;
            }

            if (animationView.layoutScaleFactor == null) {
                // Auto layout scale factor is enabled
                // Reset the view to the current auto value
                animationView.layoutScaleFactor = defaultScaleFactor
            }

            if (animationView.layoutScaleFactor!! > 1) {
                animationView.layoutScaleFactor = animationView.layoutScaleFactor?.minus(increment);
            }
            animationView.play()
        }
        findViewById<Button>(R.id.scale_auto).setOnClickListener {
            // Setting to -1 will use the device density as determined by Rive
            animationView.layoutScaleFactor = defaultScaleFactor
            animationView.play()
        }

        // ALIGNMENT BUTTONS

        findViewById<Button>(R.id.alignment_top_left).setOnClickListener {
            animationView.alignment = Alignment.TOP_LEFT
            animationView.play()
        }
        findViewById<Button>(R.id.alignment_top_center).setOnClickListener {
            animationView.alignment = Alignment.TOP_CENTER
            animationView.play()
        }
        findViewById<Button>(R.id.alignment_top_right).setOnClickListener {
            animationView.alignment = Alignment.TOP_RIGHT
            animationView.play()
        }

        findViewById<Button>(R.id.alignment_center_left).setOnClickListener {
            animationView.alignment = Alignment.CENTER_LEFT
            animationView.play()
        }
        findViewById<Button>(R.id.alignment_center).setOnClickListener {
            animationView.alignment = Alignment.CENTER
            animationView.play()
        }
        findViewById<Button>(R.id.alignment_center_right).setOnClickListener {
            animationView.alignment = Alignment.CENTER_RIGHT
            animationView.play()
        }

        findViewById<Button>(R.id.alignment_bottom_left).setOnClickListener {
            animationView.alignment = Alignment.BOTTOM_LEFT
            animationView.play()
        }
        findViewById<Button>(R.id.alignment_bottom_center).setOnClickListener {
            animationView.alignment = Alignment.BOTTOM_CENTER
            animationView.play()
        }
        findViewById<Button>(R.id.alignment_bottom_right).setOnClickListener {
            animationView.alignment = Alignment.BOTTOM_RIGHT
            animationView.play()
        }
    }
}
