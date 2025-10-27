package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.Loop

class LoopModeActivity : AppCompatActivity() {
    var direction: Direction = Direction.AUTO
    val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.loop_mode_view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.loop_mode)

        findViewById<AppCompatButton>(R.id.reset).setOnClickListener { _ ->
            animationView.reset()
        }

        findViewById<AppCompatButton>(R.id.forwards).setOnClickListener { _ ->
            direction = Direction.FORWARDS
        }
        findViewById<AppCompatButton>(R.id.auto).setOnClickListener { _ ->
            direction = Direction.AUTO
        }
        findViewById<AppCompatButton>(R.id.backwards).setOnClickListener { _ ->
            direction = Direction.BACKWARDS
        }
        findViewById<AppCompatButton>(R.id.play_oneshot).setOnClickListener { _ ->
            animationView.play(animationName = "oneshot", direction = direction)
        }
        findViewById<AppCompatButton>(R.id.play_loop).setOnClickListener { _ ->
            animationView.play(animationName = "loop", direction = direction)
        }
        findViewById<AppCompatButton>(R.id.play_pingpong).setOnClickListener { _ ->
            animationView.play(animationName = "pingpong", direction = direction)
        }

        findViewById<AppCompatButton>(R.id.loop_oneshot).setOnClickListener { _ ->
            animationView.play(animationName = "oneshot", loop = Loop.LOOP, direction = direction)
        }
        findViewById<AppCompatButton>(R.id.loop_loop).setOnClickListener { _ ->
            animationView.play(animationName = "loop", loop = Loop.LOOP, direction = direction)
        }
        findViewById<AppCompatButton>(R.id.loop_pingpong).setOnClickListener { _ ->
            animationView.play(animationName = "pingpong", loop = Loop.LOOP, direction = direction)
        }

        findViewById<AppCompatButton>(R.id.oneshot_oneshot).setOnClickListener { _ ->
            animationView.play(
                animationName = "oneshot",
                loop = Loop.ONESHOT,
                direction = direction
            )
        }
        findViewById<AppCompatButton>(R.id.oneshot_loop).setOnClickListener { _ ->
            animationView.play(animationName = "loop", loop = Loop.ONESHOT, direction = direction)
        }
        findViewById<AppCompatButton>(R.id.oneshot_pingpong).setOnClickListener { _ ->
            animationView.play(
                animationName = "pingpong",
                loop = Loop.ONESHOT,
                direction = direction
            )
        }

        findViewById<AppCompatButton>(R.id.pingpong_oneshot).setOnClickListener { _ ->
            animationView.play(
                animationName = "oneshot",
                loop = Loop.PINGPONG,
                direction = direction
            )
        }
        findViewById<AppCompatButton>(R.id.pingpong_loop).setOnClickListener { _ ->
            animationView.play(animationName = "loop", loop = Loop.PINGPONG, direction = direction)
        }
        findViewById<AppCompatButton>(R.id.pingpong_pingpong).setOnClickListener { _ ->
            animationView.play(
                animationName = "pingpong",
                loop = Loop.PINGPONG,
                direction = direction
            )
        }
    }
}
