package app.rive.runtime.example

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.Fit

/**
 * Reproducer for the Fit.LAYOUT race condition.
 *
 * Launch via: adb shell am start -n app.rive.runtime.example/.FitLayoutReproActivity
 * Then force stop and relaunch repeatedly to observe intermittent failure.
 */
class FitLayoutReproActivity : ComponentActivity() {
    companion object {
        private const val TAG = "FitLayoutRepro"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(0, (48 * density).toInt(), 0, 0)
        }

        val statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setPadding(24, 20, 24, 20)
            text = "Loading…"
            gravity = Gravity.CENTER
        }

        val riveBytes = resources.openRawResource(R.raw.layout_test).readBytes()
        val riveFile = File(riveBytes)

        val border = GradientDrawable().apply {
            setStroke((2 * density).toInt(), Color.parseColor("#666666"))
            setColor(Color.TRANSPARENT)
        }

        val riveContainer = FrameLayout(this).apply {
            foreground = border
        }

        val riveView = RiveAnimationView(this)
        riveView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (200 * density).toInt()
        )
        riveContainer.addView(riveView)

        // Call setRiveFile IMMEDIATELY, before the view has been measured (still 0×0)
        riveView.setRiveFile(
            riveFile,
            fit = Fit.LAYOUT,
            autoplay = true,
        )

        root.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(riveContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Visual comparison: two bars showing expected vs actual artboard width
        val barContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt(), 0)
        }

        val expectedLabel = TextView(this).apply {
            text = "Expected artboard width (= view width):"
            textSize = 12f
            setTextColor(Color.parseColor("#aaaaaa"))
        }
        barContainer.addView(expectedLabel)

        val expectedBar = View(this).apply {
            setBackgroundColor(Color.parseColor("#2e7d32"))
        }
        barContainer.addView(expectedBar, LinearLayout.LayoutParams(0, (24 * density).toInt()).apply {
            topMargin = (4 * density).toInt()
        })

        val actualLabel = TextView(this).apply {
            text = "Actual artboard width:"
            textSize = 12f
            setTextColor(Color.parseColor("#aaaaaa"))
            setPadding(0, (12 * density).toInt(), 0, 0)
        }
        barContainer.addView(actualLabel)

        val actualBar = View(this).apply {
            setBackgroundColor(Color.parseColor("#c62828"))
        }
        barContainer.addView(actualBar, LinearLayout.LayoutParams(0, (24 * density).toInt()).apply {
            topMargin = (4 * density).toInt()
        })

        val ratioText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, (12 * density).toInt(), 0, 0)
            gravity = Gravity.CENTER
        }
        barContainer.addView(ratioText)

        root.addView(barContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val infoText = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#666666"))
            setPadding(24, (24 * density).toInt(), 24, 16)
            text = "layout_test.riv | Fit.LAYOUT\nForce stop & relaunch to test"
            gravity = Gravity.CENTER
        }
        root.addView(infoText)

        setContentView(root)

        riveView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val viewWidthPx = right - left

            riveView.postDelayed({
                val artboard = riveView.controller.activeArtboard ?: return@postDelayed
                val abW = artboard.width
                val abH = artboard.height
                val expectedW = viewWidthPx / density
                val expectedH = (bottom - top) / density
                val ok = kotlin.math.abs(abW - expectedW) < 2f

                val msg = if (ok) {
                    "✓ Artboard %.0f dp = View %.0f dp".format(abW, expectedW)
                } else {
                    "✗ Artboard %.0f dp ≠ View %.0f dp (%.1fx too wide!)".format(
                        abW, expectedW, abW / expectedW)
                }
                Log.d(TAG, msg)
                statusText.text = msg
                statusText.setBackgroundColor(
                    if (ok) Color.parseColor("#2e7d32") else Color.parseColor("#c62828")
                )

                // Scale both bars relative to the container width
                val containerWidth = barContainer.width - barContainer.paddingLeft - barContainer.paddingRight
                val maxArtboard = maxOf(abW, expectedW)

                val expectedBarWidth = (containerWidth * (expectedW / maxArtboard)).toInt()
                val actualBarWidth = (containerWidth * (abW / maxArtboard)).toInt()

                expectedBar.layoutParams = expectedBar.layoutParams.apply { width = expectedBarWidth }
                actualBar.layoutParams = actualBar.layoutParams.apply { width = actualBarWidth }
                expectedBar.requestLayout()
                actualBar.requestLayout()

                if (ok) {
                    actualBar.setBackgroundColor(Color.parseColor("#2e7d32"))
                    ratioText.text = "Widths match ✓"
                    ratioText.setTextColor(Color.parseColor("#4caf50"))
                } else {
                    actualBar.setBackgroundColor(Color.parseColor("#c62828"))
                    ratioText.text = "Artboard is %.1f× wider than the view!".format(abW / expectedW)
                    ratioText.setTextColor(Color.parseColor("#ef5350"))
                }
            }, 500)
        }
    }
}
