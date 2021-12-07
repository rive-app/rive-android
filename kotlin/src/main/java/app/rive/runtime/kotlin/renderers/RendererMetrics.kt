package app.rive.runtime.kotlin.renderers

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

@RequiresApi(Build.VERSION_CODES.N)
class RendererMetrics(activity: Activity) : Window.OnFrameMetricsAvailableListener {
    companion object {
        private const val TAG = "RendererMetrics"
        private const val ONE_MS_IN_NS: Double = 1000000.toDouble()
        const val SAMPLES = 30
    }

    private var allFrames = 0
    private var sampleCount = 0
    private var jankyFrames = 0
    private var totalTime = BigDecimal(0.0)
    private val refreshRateMs: Float

    init {
        // Get display metrics
        val wm = activity.windowManager
        val window = activity.window
        // Let's get the system's default refresh rate in ms
        var refreshRateHz: Float = 60.0F
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.context.display?.let {
                refreshRateHz = it.refreshRate
            } ?: run {
                // Failed to get my display?
                Log.w(TAG, "Failed to get the display, defaulting to 60hz")
            }
        } else {
            @Suppress("DEPRECATION")
            refreshRateHz = window.windowManager.defaultDisplay.refreshRate
        }
        Log.i(TAG, String.format("Refresh rate: %.1f Hz", refreshRateHz))

        refreshRateMs = 1000 / refreshRateHz
    }

    override fun onFrameMetricsAvailable(
        window: Window?,
        frameMetrics: FrameMetrics?,
        dropCountSinceLastInvocation: Int
    ) {
        if (window == null) {
            Log.w(TAG, "Invalid Window reference")
            return
        }
        if (frameMetrics == null) {
            Log.w(TAG, "Invalid FrameMetrics reference")
            return
        }
        val frameMetricsCopy = FrameMetrics(frameMetrics)
        allFrames++
        sampleCount++

        val totalDurationMs =
            (frameMetricsCopy.getMetric(FrameMetrics.TOTAL_DURATION).toDouble() / ONE_MS_IN_NS)
        totalTime = totalTime.add(totalDurationMs.toBigDecimal())
        if (totalTime > refreshRateMs.toBigDecimal()) {
            jankyFrames++
        }

        // Print every SAMPLES frames.
        if (sampleCount == SAMPLES) {
            sampleCount = 0
            val drawMs =
                frameMetricsCopy.getMetric(FrameMetrics.DRAW_DURATION) / ONE_MS_IN_NS
            val swapBuffersMs =
                frameMetricsCopy.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION) / ONE_MS_IN_NS
            val issueGPUCommandsMs =
                frameMetricsCopy.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) / ONE_MS_IN_NS

            val frameValues = java.lang.String.format(
                Locale.US,
                """\n
============ FrameMetrics ============
=== Frame issued in:        %.2fms ===
=== Draw Time:              %.2fms ===
=== Swap Buffers Duration:  %.2fms ===
=== GPU commands sent in:   %.2fms ===
======================================
=== Overall average:        %.2fms ===""".trimIndent(),
                totalDurationMs,
                drawMs,
                swapBuffersMs,
                issueGPUCommandsMs,
                totalTime.divide(allFrames.toBigDecimal(), 2, RoundingMode.HALF_UP)
            )

            Log.i(TAG, frameValues)
        }
    }
}