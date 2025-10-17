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

        // FIT BUTTONS

        findViewById<Button>(R.id.fit_layout).setOnClickListener {
            animationView.fit = Fit.LAYOUT
        }
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

        // SCALE BUTTONS

        findViewById<Button>(R.id.scale_up).setOnClickListener {
            if (animationView.fit != Fit.LAYOUT) {
                return@setOnClickListener;
            }

            if (animationView.layoutScaleFactor == null) {
                // Auto layout scale factor is enabled
                // Reset the view to the current auto value
                animationView.layoutScaleFactor = animationView.layoutScaleFactorAutomatic
            }

            animationView.layoutScaleFactor = animationView.layoutScaleFactor?.plus(1);

        }
        findViewById<Button>(R.id.scale_down).setOnClickListener {
            if (animationView.fit != Fit.LAYOUT) {
                return@setOnClickListener;
            }

            if (animationView.layoutScaleFactor == null) {
                // Auto layout scale factor is enabled
                // Reset the view to the current auto value
                animationView.layoutScaleFactor = animationView.layoutScaleFactorAutomatic
            }

            if (animationView.layoutScaleFactor!! > 1) {
                animationView.layoutScaleFactor = animationView.layoutScaleFactor?.minus(1);
            }
        }
        findViewById<Button>(R.id.scale_auto).setOnClickListener {
            // Setting to -1 will use the device density as determined by Rive
            animationView.layoutScaleFactor = null;
        }

        // ALIGNMENT BUTTONS

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
}
