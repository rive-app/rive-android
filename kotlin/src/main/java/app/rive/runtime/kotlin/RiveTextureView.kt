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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.rive.runtime.kotlin.renderers.RendererSkia

abstract class RiveTextureView(context: Context, attrs: AttributeSet? = null) :
    TextureView(context, attrs),
    TextureView.SurfaceTextureListener,
    DefaultLifecycleObserver {

    companion object {
        const val TAG = "RiveTextureView"
    }
    // TODO:    private external fun cppGetAverageFps(rendererAddress: Long): Float

    init {
        // Attach the observer to give us lifecycle hooks.
        (context as? LifecycleOwner)?.lifecycle?.addObserver(this)
    }

    protected val activity by lazy(LazyThreadSafetyMode.NONE) {
        // If this fails we have a problem.
        this.getMaybeActivity()!!
    }

    protected var renderer: RendererSkia? = null
    private lateinit var viewSurface: Surface
    protected abstract fun createRenderer(): RendererSkia

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
        // If no renderer has been made, we can't move forward.
        // Only make the renderer once we are ready to display things.
        renderer = createRenderer()
        renderer!!.make()
    }

    @CallSuper
    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        viewSurface = Surface(surfaceTexture)
        renderer?.setSurface(viewSurface)
    }

    @CallSuper
    override fun onDetachedFromWindow() {
        // If we delete, we must have a Renderer.
        renderer!!.delete()
        renderer = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        when (visibility) {
            View.VISIBLE -> renderer?.start()
            else -> renderer?.stop()
        }
    }

    @CallSuper
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        viewSurface.release()
        return false
    }

    override fun onCreate(owner: LifecycleOwner) {}

    override fun onStart(owner: LifecycleOwner) {}

    override fun onResume(owner: LifecycleOwner) {}

    override fun onPause(owner: LifecycleOwner) {}

    override fun onStop(owner: LifecycleOwner) {}

    /**
     * DefaultLifecycleObserver.onDestroy() is called when the LifecycleOwner's onDestroy() method
     * is called.
     * This typically happens when the Activity or Fragment is in the process of being permanently
     * destroyed.
     */
    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
    }
}
