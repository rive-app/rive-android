package app.rive.runtime.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.renderers.RendererSkia

class Empty : AppCompatActivity() {
    private val containerView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<LinearLayout>(R.id.container)
    }
    private lateinit var swappyView: SwappyView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load Rive C++
        Rive.init()
        setContentView(R.layout.activity_empty)

        swappyView = SwappyView(this, null)
        val density = resources.displayMetrics.density
        swappyView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (366 * density).toInt(),
        )
        containerView.addView(swappyView)
    }
}

class SwappyView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs),
    SurfaceHolder.Callback, Choreographer.FrameCallback {
    private val riveRenderer = RendererSkia()
    private lateinit var file: File
    private var artboard: Artboard? = null

    private val activity: Activity?
        get() {
            var ctx = context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) {
                    return ctx
                }
                ctx = ctx.baseContext
            }
            return null
        }


    private val ONE_MS_IN_NS: Long = 1000000
    private val ONE_S_IN_NS = 1000 * ONE_MS_IN_NS

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // If this fails we have a problem.
        val activity = this.activity!!
        // Get display metrics
        val wm = activity.windowManager
        // Deprecated in API 30: keep this instead of having two separate paths.
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        val refreshRateHz = display.refreshRate
        val refreshPeriodNanos = (ONE_S_IN_NS / refreshRateHz).toLong()
        Log.i("SwappyView.kt", String.format("Refresh rate: %.1f Hz", refreshRateHz))

        holder.addCallback(this)
        nInit(activity, refreshPeriodNanos)
        val fileBytes = activity.resources.openRawResource(R.raw.duowalk).readBytes()
        file = File(fileBytes)
        file.firstArtboard.getInstance().let {
            artboard = it
            riveRenderer.artboard = artboard
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        println("surfaceCreated!")
//        riveRenderer.initializeSkia()
        nStart(holder.surface, riveRenderer.address)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        println("surfaceChanged!")
//        nSetSurface(holder.surface, width, height)
        nSetViewport(riveRenderer.address, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        println("surfaceDestroyed!")
//        nStop()
//        nClearSurface()
    }

    override fun doFrame(frameTimeNanos: Long) {
        Choreographer.getInstance().postFrameCallback(this)
    }

    private external fun nInit(activity: Activity, initialSwapIntervalNS: Long)
    private external fun nSetSurface(surface: Surface, width: Int, height: Int)
    private external fun nSetViewport(rendererAddress: Long, width: Int, height: Int)
    private external fun nClearSurface()
    private external fun nStart(surface: Surface, rendererAddress: Long)
    private external fun nStop()
}