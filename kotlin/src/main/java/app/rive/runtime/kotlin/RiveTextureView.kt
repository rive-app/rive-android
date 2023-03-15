package app.rive.runtime.kotlin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.annotation.CallSuper
import app.rive.runtime.kotlin.renderers.RendererSkia

abstract class RiveTextureView(context: Context, attrs: AttributeSet? = null) :
    TextureView(context, attrs),
    TextureView.SurfaceTextureListener {

    companion object {
        const val TAG = "RiveTextureView"
    }
    // TODO:    private external fun cppGetAverageFps(rendererAddress: Long): Float


    protected val activity by lazy(LazyThreadSafetyMode.NONE) {
        // If this fails we have a problem.
        this.getMaybeActivity()!!
    }

    protected abstract val renderer: RendererSkia
    private lateinit var viewSurface: Surface

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
        isOpaque = false
        renderer.make()
    }

    @CallSuper
    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        viewSurface = Surface(surfaceTexture)
        renderer.setSurface(viewSurface)
    }

    @CallSuper
    override fun onDetachedFromWindow() {
        renderer.delete()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // TODO: this should pause animations.
        when (visibility) {
            View.VISIBLE -> renderer.start()
            else -> renderer.stop()
        }
    }

    @CallSuper
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        viewSurface.release()
        return false
    }
}
