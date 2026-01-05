package app.rive.runtime.example

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import app.rive.runtime.kotlin.controllers.ControllerStateManagement

@ControllerStateManagement
class MainActivity : ComponentActivity() {
    private val buttonsData = listOf(
        Pair(R.id.go_compose, ComposeActivity::class.java),
        Pair(R.id.go_compose_data_binding, ComposeDataBindingActivity::class.java),
        Pair(R.id.go_compose_data_binding_artboards, ComposeArtboardBindingActivity::class.java),
        Pair(R.id.go_compose_data_binding_images, ComposeImageBindingActivity::class.java),
        Pair(R.id.go_compose_data_binding_lists, ComposeListActivity::class.java),
        Pair(R.id.go_compose_layout, ComposeLayoutActivity::class.java),
        Pair(R.id.go_compose_audio, ComposeAudioActivity::class.java),
        Pair(R.id.go_compose_touch_pass_through, ComposeTouchPassThroughActivity::class.java),
        Pair(R.id.go_compose_scrolling, ComposeScrollActivity::class.java),
        Pair(R.id.go_simple, SimpleActivity::class.java),
        Pair(R.id.go_events, EventsActivity::class.java),
        Pair(R.id.go_interactive_samples, InteractiveSamplesActivity::class.java),
        Pair(R.id.go_multiple_artboards, MultipleArtboardsActivity::class.java),
        Pair(R.id.go_android_player, AndroidPlayerActivity::class.java),
        Pair(R.id.go_loop_mode, LoopModeActivity::class.java),
        Pair(R.id.go_layout, LayoutActivity::class.java),
        Pair(R.id.go_fragment, RiveFragmentActivity::class.java),
        Pair(R.id.go_low_level, LowLevelActivity::class.java),
        Pair(R.id.go_http, HttpActivity::class.java),
        Pair(R.id.go_simple_state_machine, SimpleStateMachineActivity::class.java),
        Pair(R.id.go_nested_input, NestedInputActivity::class.java),
        Pair(R.id.go_nested_text_run, NestedTextRunActivity::class.java),
        Pair(R.id.go_button, ButtonActivity::class.java),
        Pair(R.id.go_blend, BlendActivity::class.java),
        Pair(R.id.go_metrics, MetricsActivity::class.java),
        Pair(R.id.go_assets, AssetsActivity::class.java),
        Pair(R.id.go_recycler, RecyclerActivity::class.java),
        Pair(R.id.go_viewpager, ViewPagerActivity::class.java),
        Pair(R.id.go_meshes, MeshesActivity::class.java),
        Pair(R.id.go_viewstub, ViewStubActivity::class.java),
        Pair(R.id.go_compose_legacy, LegacyComposeActivity::class.java),
        Pair(R.id.go_frame, FrameActivity::class.java),
        Pair(R.id.go_dynamic_text, DynamicTextActivity::class.java),
        Pair(R.id.go_assets_loader, AssetLoaderActivity::class.java),
        Pair(R.id.go_stress_test, StressTestActivity::class.java),
        Pair(R.id.go_rive_font_load_simple, FontLoadActivity::class.java),
        Pair(R.id.go_audio_simple, AudioAssetActivity::class.java),
        Pair(R.id.go_audio_external, AudioExternalAssetActivity::class.java),
        Pair(R.id.go_font_fallback, FontFallback::class.java),
        Pair(R.id.go_touch_passthrough, TouchPassthroughActivity::class.java),
        Pair(R.id.go_image_binding, ImageBindingActivity::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeContent(R.layout.main)

        buttonsData.forEach { pair ->
            findViewById<Button>(pair.first).setOnClickListener {
                startActivity(Intent(this, pair.second))
            }
        }
    }
}
