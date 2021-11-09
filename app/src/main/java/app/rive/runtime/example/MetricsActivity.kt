package app.rive.runtime.example

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Trace
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.renderers.RendererMetrics
import app.rive.runtime.kotlin.renderers.RendererSkia
import java.util.*


class MetricsActivity : AppCompatActivity() {
    private val containerView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<LinearLayout>(R.id.container)
    }
    private lateinit var swappyView: SwappyView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure Rive C++ is loaded.
        Rive.init()
        setContentView(R.layout.activity_metrics)

        swappyView = SwappyView(this, null)
        val density = resources.displayMetrics.density
        swappyView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (366 * density).toInt(),
        )
        swappyView.setZOrderOnTop(true)
        swappyView.holder.setFormat(PixelFormat.TRANSLUCENT)
        containerView.addView(swappyView)
        // TODO: add a second view here to check that it still works.
    }
}

class SwappyView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs),
    SurfaceHolder.Callback, Choreographer.FrameCallback {
    private val riveRenderer = RendererSkia()
    private lateinit var file: File
    private var artboard: Artboard? = null
    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null

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
        // Attaches doFrame()?
        Choreographer.getInstance().postFrameCallback(this)
        // Let's do some calculations here.
        startFrameMetrics(activity.window, 1000 / refreshRateHz)
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun startFrameMetrics(window: Window, maxFrameTime: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            frameMetricsListener = RendererMetrics()
        } else {
            Log.w(
                "Swappy@FrameMetrics",
                "FrameMetrics can work only with Android SDK 24 (Nougat) and higher"
            );
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun stopFrameMetrics(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            frameMetricsListener?.let {
                activity.window.removeOnFrameMetricsAvailableListener(it)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        println("surfaceCreated!")
        nStart(riveRenderer.address)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        println("surfaceChanged!")
        nSetViewport(holder.surface, riveRenderer.address)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        println("surfaceDestroyed!")
        nStop(riveRenderer.address)
        nClearSurface()
    }

    override fun doFrame(frameTimeNanos: Long) {
        Trace.beginSection("doFrame");
        val fpsView = activity?.findViewById<TextView>(R.id.fps)
        val fps = nGetAverageFps(riveRenderer.address)
        fpsView?.setText(
            java.lang.String.format(
                Locale.US,
                "Frame rate: %.1f Hz (%.2f ms)",
                fps,
                1e3f / fps
            )
        )
        Choreographer.getInstance().postFrameCallback(this)
        Trace.endSection()
    }

    private external fun nInit(activity: Activity, initialSwapIntervalNS: Long)
    private external fun nSetViewport(surface: Surface, rendererAddress: Long)
    private external fun nStart(rendererAddress: Long)
    private external fun nClearSurface()
    private external fun nStop(rendererAddress: Long)
    private external fun nGetAverageFps(rendererAddress: Long): Float
}