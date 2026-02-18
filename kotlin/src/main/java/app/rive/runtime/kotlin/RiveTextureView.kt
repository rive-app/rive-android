package app.rive.runtime.kotlin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.annotation.CallSuper
import androidx.lifecycle.LifecycleObserver
import app.rive.runtime.kotlin.core.RefCount
import app.rive.runtime.kotlin.renderers.Renderer
import java.util.concurrent.atomic.AtomicInteger

/**
 * A reference-counted wrapper around an Android [Surface]. It ensures that the
 * underlying `Surface` is released only when it's no longer needed by anyone.
 *
 * This class is crucial for managing the lifecycle of a `Surface` shared
 * between the UI thread (via [RiveTextureView]) and the Rive renderer thread. The
 * [RiveTextureView] creates the `Surface` and holds the initial reference. When
 * the `Surface` is passed to the [Renderer], the renderer also acquires a
 * reference.
 *
 * This mechanism prevents the `Surface` from being prematurely released by the
 * UI thread (e.g., during view detachment) while the renderer thread might still
 * be in the process of drawing to it. The `release()` method decrements the
 * reference count, and the underlying [Surface.release] is only called when the
 * count drops to zero.
 *
 * @param surface The underlying Android [Surface] to be managed.
 */
internal class SharedSurface(val surface: Surface) : RefCount {
    // Start with one reference for the creator (RiveTextureView).
    override var refs = AtomicInteger(1)

    override fun release(): Int {
        val count = super.release()
        if (count == 0) {
            // Only release the underlying surface when no one holds a reference.
            surface.release()
        }
        return count
    }
}

abstract class RiveTextureView(context: Context, attrs: AttributeSet? = null) :
    TextureView(context, attrs),
    TextureView.SurfaceTextureListener {

    protected val activity by lazy(LazyThreadSafetyMode.NONE) {
        // If this fails we have a problem.
        getContextAsType<Activity>()!!
    }

    protected val lifecycleObserver: LifecycleObserver by lazy { createObserver() }
    protected var renderer: Renderer? = null
    private var sharedSurface: SharedSurface? = null
    protected abstract fun createRenderer(): Renderer
    protected abstract fun createObserver(): LifecycleObserver
    protected inline fun <reified T> getContextAsType(): T? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is T) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {} // called every time when swapBuffers is called
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        onSurfaceTextureAvailable(surface, width, height)
    }

    @CallSuper
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Register this SurfaceView for the SurfaceHolder callbacks below
        surfaceTextureListener = this
        isOpaque = false
        // If no renderer has been made, we can't move forward.
        // Only make the renderer once we are ready to display things.
        renderer = createRenderer().apply { make() }
    }

    @CallSuper
    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        sharedSurface?.release()
        renderer?.apply {
            stop()
            val viewSurface = Surface(surfaceTexture)
            sharedSurface = SharedSurface(viewSurface)
            setSurface(sharedSurface!!)
        }
    }

    @CallSuper
    override fun onDetachedFromWindow() {
        sharedSurface?.release()
        sharedSurface = null
        // If we delete, we must have a Renderer.
        renderer!!.delete()
        renderer = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        when (visibility) {
            VISIBLE -> renderer?.start()
            else -> renderer?.stop()
        }
    }

    @CallSuper
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderer?.destroySurfaceAsync()
        sharedSurface?.release()
        sharedSurface = null
        return false
    }
}
