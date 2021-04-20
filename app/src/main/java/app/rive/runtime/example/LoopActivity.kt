package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatToggleButton
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.Rive
import kotlinx.android.synthetic.main.loopy.view.*
import kotlin.math.absoluteValue

class LoopActivity : AppCompatActivity() {

    val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.loopy)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init()
        setContentView(R.layout.loopy)

        findViewById<AppCompatButton>(R.id.reset).setOnClickListener { _->
            animationView.reset()
        }

        findViewById<AppCompatButton>(R.id.forwards).setOnClickListener { _->
            animationView.direction(Direction.FORWARDS)
        }
        findViewById<AppCompatButton>(R.id.backwards).setOnClickListener { _->
            animationView.direction(Direction.BACKWARDS)
        }
        findViewById<AppCompatButton>(R.id.play_oneshot).setOnClickListener { _->
            animationView.play(animationName = "oneshot")
        }
        findViewById<AppCompatButton>(R.id.play_loop).setOnClickListener { _->
            animationView.play(animationName = "loop")
        }
        findViewById<AppCompatButton>(R.id.play_pingpong).setOnClickListener { _->
            animationView.play(animationName = "pingpong")
        }

        findViewById<AppCompatButton>(R.id.loop_oneshot).setOnClickListener { _->
            animationView.play(animationName = "oneshot", loop=Loop.LOOP)
        }
        findViewById<AppCompatButton>(R.id.loop_loop).setOnClickListener { _->
            animationView.play(animationName = "loop", loop=Loop.LOOP)
        }
        findViewById<AppCompatButton>(R.id.loop_pingpong).setOnClickListener { _->
            animationView.play(animationName = "pingpong", loop=Loop.LOOP)
        }

        findViewById<AppCompatButton>(R.id.oneshot_oneshot).setOnClickListener { _->
            animationView.play(animationName = "oneshot", loop=Loop.ONESHOT)
        }
        findViewById<AppCompatButton>(R.id.oneshot_loop).setOnClickListener { _->
            animationView.play(animationName = "loop", loop=Loop.ONESHOT)
        }
        findViewById<AppCompatButton>(R.id.oneshot_pingpong).setOnClickListener { _->
            animationView.play(animationName = "pingpong", loop=Loop.ONESHOT)
        }

        findViewById<AppCompatButton>(R.id.pingpong_oneshot).setOnClickListener { _->
            animationView.play(animationName = "oneshot", loop=Loop.PINGPONG)
        }
        findViewById<AppCompatButton>(R.id.pingpong_loop).setOnClickListener { _->
            animationView.play(animationName = "loop", loop=Loop.PINGPONG)
        }
        findViewById<AppCompatButton>(R.id.pingpong_pingpong).setOnClickListener { _->
            animationView.play(animationName = "pingpong", loop=Loop.PINGPONG)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        animationView.reset()
    }
}
