package app.rive.runtime.kotlin.renderers

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import java.math.BigDecimal
import java.util.*

@RequiresApi(Build.VERSION_CODES.N)
class RendererMetrics(activity: Activity) : Window.OnFrameMetricsAvailableListener {
    private val TAG = "RendererMetrics"
    private val ONE_MS_IN_NS: Long = 1000000
    private val ONE_S_IN_NS = 1000 * ONE_MS_IN_NS

    private var allFrames = 0
    private var jankyFrames = 0
    private var totalTime = BigDecimal(0.0)

    init {
        // Get display metrics
        val wm = activity.windowManager
        // Deprecated in API 30: keep this instead of having two separate paths.
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        val refreshRateHz = display.refreshRate
        val refreshPeriodNanos = (ONE_S_IN_NS / refreshRateHz).toLong()
        Log.i(this.TAG, String.format("Refresh rate: %.1f Hz", refreshRateHz))
    }

    override fun onFrameMetricsAvailable(
        window: Window?,
        frameMetrics: FrameMetrics?,
        dropCountSinceLastInvocation: Int
    ) {
        if (window == null) {
            Log.w(this.TAG, "Invalid Window reference")
            return
        }
        if (frameMetrics == null) {
            Log.w(this.TAG, "Invalid FrameMetrics reference")
            return
        }
        val frameMetricsCopy = FrameMetrics(frameMetrics)
        allFrames++

        // Let's get the system's default refresh rate in ms
        var refreshRateHz: Float = 60.0F
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.context.display?.let {
                refreshRateHz = it.refreshRate
            } ?: run {
                // Failed to get my display?
                Log.w(this.TAG, "Failed to get the display, defaulting to 60hz")
            }
        } else {
            @Suppress("DEPRECATION")
            refreshRateHz = window.windowManager.defaultDisplay.refreshRate
        }
        val refreshRateMs = 1000 / refreshRateHz


        val totalDurationMs =
            (0.000001 * frameMetricsCopy.getMetric(FrameMetrics.TOTAL_DURATION))
        totalTime.add(BigDecimal(totalDurationMs))
        if (totalDurationMs > refreshRateMs) {
            jankyFrames++
        }

        // Value returned is in NS: convert it to MS
        val drawMs =
            frameMetricsCopy.getMetric(FrameMetrics.DRAW_DURATION) / ONE_MS_IN_NS.toDouble()
        val swapBuffersMs =
            frameMetricsCopy.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION) / ONE_MS_IN_NS.toDouble()
        val issueGPUCommandsMs =
            frameMetricsCopy.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) / ONE_MS_IN_NS.toDouble()

        val frameValues = java.lang.String.format(
            Locale.US,
            """
                === Frame issued in:        %.2fms ===
                === Draw Time:              %.2fms ===
                === Swap Buffers Duration:  %.2fms ===
                === GPU commands sent in:   %.2fms ===
                ======================================
                === Overall average:        %.2fms ===
            """.trimIndent(),
            totalDurationMs,
            drawMs,
            swapBuffersMs,
            issueGPUCommandsMs,
            totalTime.divide(BigDecimal(allFrames))
        )

        Log.i(this.TAG, frameValues)
    }
}