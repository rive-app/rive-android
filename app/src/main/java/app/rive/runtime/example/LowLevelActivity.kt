package app.rive.runtime.example

import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.*


class LowLevelActivity : AppCompatActivity() {
    // Rive renderer
    private val renderer = Renderer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_low_level)

        // Hides the app/action bar
        supportActionBar?.hide();

        // Load the Rive from a raw resource
        val file = File(resources.openRawResource(R.raw.basketball).readBytes())

        // Get the first artboard in the file
        val artboard = file.firstArtboard

        // Attach the Rive view to the activity's root layout
        val layout = findViewById<ViewGroup>(R.id.low_level_view_root)
        val riveView = LowLevelRiveView(renderer, artboard, this)
        riveView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        layout.addView(riveView)
    }

    // Clean up the Rive renderer
    override fun onDestroy() {
        super.onDestroy()
        renderer.cleanup()
    }
}

// Low level Rive view
class LowLevelRiveView: View {
    private val renderer: Renderer
    private val artboard: Artboard
    private val instance: LinearAnimationInstance
    private var lastTime: Long = 0
    private lateinit var bounds: AABB

    constructor(renderer: Renderer, artboard: Artboard, context: Context) : super(context) {
        this.renderer = renderer
        this.artboard = artboard
        instance = LinearAnimationInstance(artboard.firstAnimation)
        bounds = AABB(100F, 100F)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastTime = System.currentTimeMillis()
    }

    // When the size of the view changes, update the Rive drawing bounds
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh);
        bounds = AABB(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Calculate the time to advance the animation
        val currentTime = System.currentTimeMillis()
        val elapsed = (currentTime - lastTime) / 1000f
        lastTime = currentTime

        // Set the Rive renderer's canvas and alignment
        renderer.canvas = canvas
        renderer.align(Fit.COVER, Alignment.CENTER, bounds, artboard.bounds)

        // Advance the animation(s) with a given mix value
        instance.advance(elapsed)
        instance.apply(artboard, 1f)

        // Advance the artboard
        artboard.advance(elapsed)

        // Draw the artboard
        canvas.save()
        artboard.draw(renderer)
        canvas.restore()

        // Draw again on the next frame
        invalidate()
    }

}