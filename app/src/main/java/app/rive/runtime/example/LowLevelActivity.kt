package app.rive.runtime.example

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import app.rive.runtime.kotlin.RiveTextureView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.LinearAnimationInstance
import app.rive.runtime.kotlin.renderers.Renderer


class LowLevelActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_low_level)

        // Hides the app/action bar
        supportActionBar?.hide()

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
    private lateinit var file: File

    // Objects that the renderer needs for drawing
    private lateinit var artboard: Artboard
    private lateinit var animationInstance: LinearAnimationInstance

    private fun setupFile(renderer: Renderer) {
        val resource = resources.openRawResource(R.raw.basketball)
        // Keep a reference to the file to keep resources around.
        file = File(resource.readBytes())
        resource.close()
        artboard = file.firstArtboard
        animationInstance = artboard.firstAnimation

        // This will be deleted with its dependents.
        renderer.dependencies.add(file)
    }

    override fun createObserver(): LifecycleObserver {
        return object : DefaultLifecycleObserver {
            /* Optionally override lifecycle methods. */
            // override fun onCreate(owner: LifecycleOwner) {
            //     super.onCreate(owner)
            // }
            // override fun onDestroy(owner: LifecycleOwner) {
            //     super.onDestroy(owner)
            // }
        }
    }

    override fun createRenderer(): Renderer {
        val renderer = object : Renderer() {

            override fun draw() {
                artboard.let {
                    save()
                    align(Fit.COVER, Alignment.CENTER, RectF(0.0f, 0.0f, width, height), it.bounds)
                    it.drawSkia(
                        cppPointer
                    )
                    restore()
                }
            }

            override fun advance(elapsed: Float) {
                animationInstance.advance(elapsed)
                animationInstance.apply()
                artboard.advance(elapsed)
            }
        }
        // Call setup file only once we created the renderer.
        setupFile(renderer)
        return renderer
    }

}