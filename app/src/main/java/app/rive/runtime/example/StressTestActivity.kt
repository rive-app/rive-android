package app.rive.runtime.example

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.WorkerThread
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
import java.util.Locale
import kotlin.math.min


class StressTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stress_test)

        // Hides the app/action bar
        supportActionBar?.hide()

        // Attach the Rive view to the activity's root layout
        val layout = findViewById<ViewGroup>(R.id.low_level_view_root)
        val riveView = StressTestView(this)

        layout.addView(riveView)
    }
}

class StressTestView(context: Context) : RiveTextureView(context) {
    private var instanceCount: Int = 1
    private var totalElapsed: Float = 0f
    private var totalFrames: Int = 0

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

            @WorkerThread
            override fun draw() {
                synchronized(frameLock) {
                    if (!isAttached || !hasCppObject) {
                        return
                    }
                    artboard.let {
                        save()
                        align(
                            Fit.CONTAIN,
                            Alignment.CENTER,
                            RectF(0f, 0f, width, height),
                            it.bounds
                        )
                        val rows = (instanceCount + 6) / 7
                        val cols = min(instanceCount, 7)
                        translate(0f, (rows - 1) * -.5f * 200f)
                        for (j in 1..rows) {
                            save()
                            translate((cols - 1) * -.5f * 125f, 0f)
                            for (i in 1..cols) {
                                it.draw(cppPointer)
                                translate(125f, 0f)
                            }
                            restore()
                            translate(0f, 200f)
                        }
                        restore()
                    }
                }
            }

            @WorkerThread
            override fun advance(elapsed: Float) {
                synchronized(frameLock) {
                    if (!isAttached || !hasCppObject) {
                        return
                    }
                    // Actually advance the animation here. draw() will also call advance(), but the
                    // purpose of that is to draw each Marty at a slightly different animation offset,
                    // and it will loop back around to the original animation location.
                    animationInstance.advanceAndGetResult(elapsed)
                    animationInstance.apply()
                    artboard.advance(elapsed)

                    totalElapsed += elapsed
                    totalFrames++

                    if (totalElapsed > 1f) {
                        val fps = totalFrames / totalElapsed
                        val fpsView =
                            ((parent as ViewGroup).parent as ViewGroup).getChildAt(1) as TextView
                        fpsView.text =
                            java.lang.String.format(
                                Locale.US,
                                "%d instances @ %.1f FPS (%.2f ms)",
                                instanceCount,
                                fps,
                                1e3f / fps
                            )
                        totalElapsed = 0f
                        totalFrames = 0
                    }
                }
            }
        }
        // Call setup file only once we created the renderer.
        setupFile(renderer)
        return renderer
    }

    private lateinit var file: File

    // Objects that the renderer needs for drawing
    private lateinit var artboard: Artboard
    private lateinit var animationInstance: LinearAnimationInstance

    private fun setupFile(renderer: Renderer) {
        // Keep a reference to the file to keep resources around.
        file = resources.openRawResource(R.raw.marty).use { File(it.readBytes()) }
        artboard = file.firstArtboard
        animationInstance = artboard.animation(1)

        // This will be deleted with its dependents.
        renderer.dependencies.add(file)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val action: Int = event.actionMasked

        return when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (instanceCount < 7)
                    instanceCount += 2
                else
                    instanceCount += 7
                totalElapsed = 0f
                totalFrames = 0
                val fpsView =
                    ((parent as ViewGroup).parent as ViewGroup).getChildAt(1) as TextView
                fpsView.text = java.lang.String.format("%d instances", instanceCount)
                true
            }

            else -> super.onTouchEvent(event)
        }
    }
}