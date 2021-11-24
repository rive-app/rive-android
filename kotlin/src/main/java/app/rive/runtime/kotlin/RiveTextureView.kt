package app.rive.runtime.kotlin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.CallSuper
import app.rive.runtime.kotlin.renderers.RendererSwappy

abstract class RiveTextureView(context: Context, attrs: AttributeSet? = null) :
    TextureView(context, attrs),
    TextureView.SurfaceTextureListener {

    private external fun cppInit(activity: Activity, initialSwapIntervalNS: Long)
    // TODO:    private external fun cppGetAverageFps(rendererAddress: Long): Float

    var isRunning: Boolean = true
        private set

    protected val activity by lazy(LazyThreadSafetyMode.NONE) {
        // If this fails we have a problem.
        this.getMaybeActivity()!!
    }

    protected abstract val renderer: RendererSwappy

    private val refreshPeriodNanos: Long by lazy {
        val msInNS: Long = 1000000
        val sInNS = 1000 * msInNS
        // Deprecated in API 30: keep this instead of having two separate paths.
        @Suppress("DEPRECATION")
        val refreshRateHz = activity.windowManager.defaultDisplay.refreshRate
        Log.i("RiveSurfaceHolder", String.format("Refresh rate: %.1f Hz", refreshRateHz))
        (sInNS / refreshRateHz).toLong()
    }

    private fun getMaybeActivity(): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {} // called every time when swapBuffers is called
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    @CallSuper
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Register this SurfaceView for the SurfaceHolder callbacks below
        surfaceTextureListener = this
        isRunning = true
        isOpaque = false
    }

    @CallSuper
    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        cppInit(activity, refreshPeriodNanos)
        val surface = Surface(surfaceTexture)
        renderer.setSurface(surface)
        renderer.start()
        isRunning = true
    }

    @CallSuper
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer.cleanup()
        isRunning = false
    }

    @CallSuper
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        isRunning = false
        // TODO: surface.release() manually, after the last render?
        //      There might be a race condition with the threading model here,
        //          maybe the reason behind dequeue buffer fails.
        return false
    }
}
