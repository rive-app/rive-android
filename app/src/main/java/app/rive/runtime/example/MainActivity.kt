package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatToggleButton
import app.rive.runtime.kotlin.Loop
import app.rive.runtime.kotlin.Rive
import app.rive.runtime.kotlin.RiveAnimationView

class MainActivity : AppCompatActivity() {

    val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.rive_animation)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init()
        setContentView(R.layout.activity_main)

        animationView.setAnimation(R.raw.flux_capacitor)
        animationView.setRepeatMode(Loop.LOOP)
        animationView.start()

        val togglePlayback = findViewById<AppCompatToggleButton>(R.id.toggle)
        togglePlayback.isChecked = animationView.isRunning
        togglePlayback.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                animationView.start()
            } else {
                animationView.pause()
            }
        }

        val reset = findViewById<AppCompatButton>(R.id.reset)
        reset.setOnClickListener {
            animationView.reset()
            togglePlayback.isChecked = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        animationView.reset()
    }
}
