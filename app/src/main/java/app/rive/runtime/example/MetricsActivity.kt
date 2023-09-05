package app.rive.runtime.example

import android.os.Bundle
import android.view.Choreographer
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.example.databinding.ActivityMetricsBinding
import app.rive.runtime.kotlin.RiveAnimationView
import java.util.Locale


class MetricsActivity : AppCompatActivity(), Choreographer.FrameCallback {

    private lateinit var binding: ActivityMetricsBinding

    private val riveView: RiveAnimationView by lazy(LazyThreadSafetyMode.NONE) {
        binding.riveView
    }

    private fun updateFps() {
        val renderer = riveView.artboardRenderer
        val fps =
            if (renderer?.hasCppObject == true) riveView.artboardRenderer!!.averageFps else -1f
        binding.fps.text =
            java.lang.String.format(
                Locale.US,
                "Frame rate: %.1f Hz (%.2f ms)",
                fps,
                1e3f / fps
            )
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMetricsBinding.inflate(layoutInflater)
        setContentView(binding.root)
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