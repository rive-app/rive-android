package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatToggleButton
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive

fun max(a:Int, b:Int):Int {
    if (a>b) return a
    return b
}

fun min(a:Int, b:Int):Int {
    if (a<b) return a
    return b
}

class AnimationsActivity : AppCompatActivity() {
    var index = 0;

    val resources = listOf(
        R.raw.off_road_car_blog,
        R.raw.flux_capacitor,
        R.raw.basketball,
        R.raw.explorer,
        R.raw.f22,
        R.raw.mascot,
        R.raw.progress,
        R.raw.pull,
        R.raw.rope,
        R.raw.trailblaze,
        R.raw.vader,
        R.raw.wacky
    )
    val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.rive_animation)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init()
        setContentView(R.layout.animations)

        val togglePlayback = findViewById<AppCompatToggleButton>(R.id.toggle)
        togglePlayback.isChecked = animationView.isPlaying
        togglePlayback.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                animationView.play()
            } else {
                animationView.pause()
            }
        }

        val reset = findViewById<AppCompatButton>(R.id.reset)
        reset.setOnClickListener {
            animationView.reset()
            togglePlayback.isChecked = false
        }
        val previous = findViewById<AppCompatButton>(R.id.previous)
        previous.setOnClickListener {
            index = max(0, --index)
            animationView.setRiveResource(resources[index])
        }
        val next = findViewById<AppCompatButton>(R.id.next)
        next.setOnClickListener {
            index = min(resources.size-1, ++index)
            animationView.setRiveResource(resources[index])
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        animationView.reset()
    }
}
