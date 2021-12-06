package app.rive.runtime.example

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        findViewById<Button>(R.id.go_simple).setOnClickListener {
            startActivity(
                Intent(this, SimpleActivity::class.java)
            )
        }

        findViewById<Button>(R.id.go_multiple_artboards).setOnClickListener {
            startActivity(
                Intent(this, MultipleArtboardsActivity::class.java)
            )
        }

        findViewById<Button>(R.id.go_android_player).setOnClickListener {
            startActivity(
                Intent(this, AndroidPlayerActivity::class.java)
            )
        }

        findViewById<Button>(R.id.go_loop_mode).setOnClickListener {
            startActivity(
                Intent(this, LoopModeActivity::class.java)
            )
        }

        findViewById<Button>(R.id.go_layout).setOnClickListener {
            startActivity(
                Intent(this, LayoutActivity::class.java)
            )
        }

        findViewById<Button>(R.id.go_fragment).setOnClickListener {
            startActivity(
                Intent(this, RiveFragmentActivity::class.java)
            )
        }

        findViewById<Button>(R.id.go_low_level).setOnClickListener {
            startActivity(
                Intent(this, LowLevelActivity::class.java)
            )
        }

        findViewById<Button>(R.id.go_http).setOnClickListener {
            startActivity(
                Intent(this, HttpActivity::class.java)
            )
        }

        findViewById<Button>(R.id.go_simple_state_machine).setOnClickListener {
            startActivity(
                Intent(this, SimpleStateMachineActivity::class.java)
            )
        }

        findViewById<Button>(R.id.go_button).setOnClickListener {
            startActivity(
                Intent(this, ButtonActivity::class.java)
            )
        }
        findViewById<Button>(R.id.go_blend).setOnClickListener {
            startActivity(
                Intent(this, BlendActivity::class.java)
            )
        }
        findViewById<Button>(R.id.go_metrics).setOnClickListener {
            startActivity(
                Intent(this, MetricsActivity::class.java)
            )
        }
        findViewById<Button>(R.id.go_assets).setOnClickListener {
            startActivity(
                Intent(this, AssetsActivity::class.java)
            )
        }
    }
}
