package app.rive.runtime.example

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import app.rive.RiveLog
import app.rive.runtime.example.utils.setEdgeToEdgeContent

class MainActivity : ComponentActivity() {
    private val buttonsData = listOf(
        Pair(R.id.go_compose, ComposeActivity::class.java),
        Pair(R.id.go_compose_data_binding, ComposeDataBindingActivity::class.java),
        Pair(R.id.go_compose_data_binding_artboards, ComposeArtboardBindingActivity::class.java),
        Pair(R.id.go_compose_data_binding_images, ComposeImageBindingActivity::class.java),
        Pair(R.id.go_compose_data_binding_lists, ComposeListActivity::class.java),
        Pair(R.id.go_compose_layout, ComposeLayoutActivity::class.java),
        Pair(R.id.go_compose_audio, ComposeAudioActivity::class.java),
        Pair(R.id.go_compose_capped_fps, ComposeCappedFPSActivity::class.java),
        Pair(R.id.go_compose_touch_pass_through, ComposeTouchPassThroughActivity::class.java),
        Pair(R.id.go_compose_scrolling, ComposeScrollActivity::class.java),
        Pair(R.id.go_scripting, ScriptingActivity::class.java),
        Pair(R.id.go_rive_snapshot, RiveSnapshotActivity::class.java),
        Pair(R.id.go_hardware_bitmap_canvas, HardwareBitmapCanvasActivity::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeContent(R.layout.main)

        RiveLog.logger = RiveLog.LogcatLogger()

        buttonsData.forEach { pair ->
            findViewById<Button>(pair.first).setOnClickListener {
                startActivity(Intent(this, pair.second))
            }
        }
    }
}
