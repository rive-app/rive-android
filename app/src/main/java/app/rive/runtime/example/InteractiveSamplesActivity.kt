package app.rive.runtime.example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView
import java.util.Calendar

class InteractiveSamplesActivity : AppCompatActivity() {
    private var keepGoing = true
    fun setTime() {
        val hours =
            (Calendar.getInstance().get(Calendar.HOUR) % 12f + Calendar.getInstance()
                .get(Calendar.MINUTE) / 60f + Calendar.getInstance()
                .get(Calendar.SECOND) / 3600f)
        clockView.setNumberState("Time", "isTime", hours)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.interactive_samples)
        setTime()
        val h = Handler(Looper.getMainLooper())
        h.postDelayed(object : Runnable {
            override fun run() {
                // do stuff then
                // can call h again after work!
                if (keepGoing) {
                    setTime()
                    h.postDelayed(this, 360)
                }
            }
        }, 360) // 1 second dela

    }

    override fun onDetachedFromWindow() {
        // This the exit point for any RiveAnimationView, if we try to access
        // underlying properties (e.g. setNumberState() above) _after_ we detached, underlying
        // objects have probably been deallocated and this will result in a crash.
        keepGoing = false
        super.onDetachedFromWindow()
    }

    private val clockView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.clock)
    }
}
