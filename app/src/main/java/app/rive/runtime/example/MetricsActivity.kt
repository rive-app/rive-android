package app.rive.runtime.example

import android.os.Bundle
import android.view.Choreographer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView
import java.util.*


class MetricsActivity : AppCompatActivity(), Choreographer.FrameCallback {

    private val riveView: RiveAnimationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById(R.id.rive_view)
    }

    private fun updateFps() {
        val fpsView = findViewById<TextView>(R.id.fps)
        val renderer = riveView.renderer
        val fps = if (renderer.hasCppObject) riveView.renderer.averageFps else -1f
        fpsView?.text =
            java.lang.String.format(
                Locale.US,
                "Frame rate: %.1f Hz (%.2f ms)",
                fps,
                1e3f / fps
            )
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_metrics)
    }

    override fun onResume() {
        super.onResume()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onPause() {
        super.onPause()
        // Stop scheduling new callbacks when losing visibility.
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        updateFps()
        Choreographer.getInstance().postFrameCallback(this)
    }
}