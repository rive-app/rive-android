package app.rive.runtime.example

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveTextureView
import app.rive.runtime.kotlin.core.*
import app.rive.runtime.kotlin.renderers.RendererSkia


class LowLevelActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_low_level)

        // Hides the app/action bar
        supportActionBar?.hide();

        // Attach the Rive view to the activity's root layout
        val layout = findViewById<ViewGroup>(R.id.low_level_view_root)
        val riveView = LowLevelRiveView(this)
        riveView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (366 * resources.displayMetrics.density).toInt(),
        )
        layout.addView(riveView)
    }
}

class LowLevelRiveView(context: Context) : RiveTextureView(context) {
    // Initialize renderer first: we can't create Files without one.
    override val renderer = object : RendererSkia() {
        override fun draw() {
            artboard.drawSkia(cppPointer, Fit.COVER, Alignment.CENTER)
        }

        override fun advance(elapsed: Float) {
            instance.advance(elapsed)
            instance.apply(artboard)
            artboard.advance(elapsed)
        }
    }

    // Keep a reference to the file to keep resources around.
    private val file: File = File(resources.openRawResource(R.raw.basketball).readBytes())

    // Objects that the renderer needs for drawing
    private var artboard: Artboard = file.firstArtboard
    private var instance: LinearAnimationInstance = LinearAnimationInstance(artboard.firstAnimation)

    private var bounds: AABB = AABB(100f, 100f)

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        bounds = AABB(width.toFloat(), height.toFloat())
    }
}