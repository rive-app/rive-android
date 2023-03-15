package app.rive.runtime.example

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val buttonsData = listOf(
        Pair(R.id.go_simple, SimpleActivity::class.java),
        Pair(R.id.go_events, EventsActivity::class.java),
        Pair(R.id.go_multiple_artboards, MultipleArtboardsActivity::class.java),
        Pair(R.id.go_android_player, AndroidPlayerActivity::class.java),
        Pair(R.id.go_loop_mode, LoopModeActivity::class.java),
        Pair(R.id.go_layout, LayoutActivity::class.java),
        Pair(R.id.go_fragment, RiveFragmentActivity::class.java),
        Pair(R.id.go_low_level, LowLevelActivity::class.java),
        Pair(R.id.go_http, HttpActivity::class.java),
        Pair(R.id.go_simple_state_machine, SimpleStateMachineActivity::class.java),
        Pair(R.id.go_button, ButtonActivity::class.java),
        Pair(R.id.go_blend, BlendActivity::class.java),
        Pair(R.id.go_metrics, MetricsActivity::class.java),
        Pair(R.id.go_assets, AssetsActivity::class.java),
        Pair(R.id.go_recycler, RecyclerActivity::class.java),
        Pair(R.id.go_viewpager, ViewPagerActivity::class.java),
        Pair(R.id.go_meshes, MeshesActivity::class.java),
        Pair(R.id.go_viewstub, ViewStubActivity::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        buttonsData.forEach { pair ->
            findViewById<Button>(pair.first).setOnClickListener {
                startActivity(Intent(this, pair.second))
            }
        }
    }
}
