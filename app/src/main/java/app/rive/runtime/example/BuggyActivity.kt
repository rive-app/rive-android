package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatToggleButton
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import kotlin.math.absoluteValue

class BuggyActivity : AppCompatActivity() {

    val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.buggy_animation)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init()
        setContentView(R.layout.buggy)

        findViewById<AppCompatToggleButton>(R.id.toggle_idle).setOnCheckedChangeListener { _, isChecked->
            if(isChecked) {
                animationView.play(animationName = "idle")
            }else {
                animationView.pause(animationName = "idle")
            }
        }
        findViewById<AppCompatToggleButton>(R.id.toggle_wipers).setOnCheckedChangeListener { _, isChecked->
            if(isChecked) {
                animationView.play(animationName = "windshield_wipers")
            }else {
                animationView.pause(animationName = "windshield_wipers")
            }
        }
        findViewById<AppCompatToggleButton>(R.id.toggle_bouncing).setOnCheckedChangeListener { _, isChecked->
            if(isChecked) {
                animationView.play(animationName = "bouncing")
            }else {
                animationView.pause(animationName = "bouncing")
            }
        }
        findViewById<AppCompatToggleButton>(R.id.toggle_broken).setOnCheckedChangeListener { _, isChecked->
            if(isChecked) {
                animationView.play(animationName = "broken")
            }else {
                animationView.pause(animationName = "broken")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        animationView.reset()
    }
}
