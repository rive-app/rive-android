package app.rive.runtime.example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView
import java.util.*

class EventsActivity : AppCompatActivity() {
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
        setContentView(R.layout.events)
        setTime()
        val h = Handler(Looper.getMainLooper())
        h.postDelayed(object : Runnable {
            override fun run() {
                // do stuff then
                // can call h again after work!
                setTime()
                if (keepGoing)
                    h.postDelayed(this, 360)
            }
        }, 360) // 1 second dela

    }

    override fun onDestroy() {
        keepGoing = false
        super.onDestroy()
    }

    private val clockView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.clock)
    }
}
