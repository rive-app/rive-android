package app.rive

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.core.ArtboardHandle
import app.rive.core.RiveSurface
import app.rive.core.StateMachineHandle
import app.rive.core.withFrameNanosChoreo
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Note: This class is more experimental than others. It is not recommended for use at this time.
 */
@ExperimentalRiveComposeAPI()
class RiveUIView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {
    private var riveFile: RiveFile? = null
    private var artboardHandle: ArtboardHandle? = null
    private var stateMachineHandle: StateMachineHandle? = null

    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var riveSurface: RiveSurface? = null
        set(value) {
            if (field != null) {
                field?.dispose()
            }
            field = value
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val owner = findViewTreeLifecycleOwner()
            ?: error("RiveUIView must be hosted under a LifecycleOwner.")

        // TODO: Refcount the file instead?
        riveFile?.commandQueue?.acquire("RiveUIView")

        owner.lifecycleScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastFrameTimeNs = 0L
                while (isActive) {
                    val deltaTimeNs = withFrameNanosChoreo { frameTimeNs ->
                        (if (lastFrameTimeNs == 0L) 0L else frameTimeNs - lastFrameTimeNs).also {
                            lastFrameTimeNs = frameTimeNs
                        }
                    }

                    val file = riveFile ?: continue
                    val cq = file.commandQueue
                    val art = artboardHandle ?: continue
                    val sm = stateMachineHandle ?: continue
                    val rs = riveSurface ?: continue

                    cq.advanceStateMachine(sm, deltaTimeNs)
                    cq.draw(art, sm, Fit.CONTAIN, Alignment.CENTER, rs)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        riveFile?.commandQueue?.release("RiveUIView", "Detached from window")
    }

    val textureView = TextureView(context).apply {
        layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                newSurfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                surface = Surface(newSurfaceTexture)
                surfaceWidth = width
                surfaceHeight = height
                riveFile?.let { file ->
                    riveSurface = file.commandQueue.createRiveSurface(surface!!)
                }
            }

            override fun onSurfaceTextureDestroyed(destroyedSurfaceTexture: SurfaceTexture): Boolean {
                riveSurface = null
                surface = null
                return true
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture, width: Int, height: Int
            ) {
                TODO("Not yet implemented")
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }
    }

    init {
        addView(textureView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    fun setRiveFile(
        file: RiveFile,
        artboard: Artboard? = null,
        stateMachineName: String? = null
    ) {
        riveFile = file
        artboardHandle =
            artboard?.artboardHandle ?: file.commandQueue.createDefaultArtboard(file.fileHandle)
        stateMachineHandle = if (stateMachineName != null)
            file.commandQueue.createStateMachineByName(artboardHandle!!, stateMachineName)
        else
            file.commandQueue.createDefaultStateMachine(artboardHandle!!)

        if (surface != null && riveSurface == null) {
            riveSurface = file.commandQueue.createRiveSurface(surface!!)
        }
    }
}
