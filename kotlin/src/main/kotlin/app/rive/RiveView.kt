package app.rive

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
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
import app.rive.core.SurfaceTextureSurface
import app.rive.core.traceSection
import app.rive.core.withFrameNanosChoreographer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Note: This class is more experimental than others. It is not recommended for use at this time.
 */
class RiveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {
    private var riveFile: RiveFile? = null
    private var artboardHandle: ArtboardHandle? = null
    private var stateMachineHandle: StateMachineHandle? = null

    private var surfaceTexture: SurfaceTexture? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var riveSurface: RiveSurface? = null
        set(value) {
            if (field != null) {
                field?.close()
            }
            field = value
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val owner = findViewTreeLifecycleOwner()
            ?: error("RiveView must be hosted under a LifecycleOwner.")

        // TODO: Refcount the file instead?
        riveFile?.riveWorker?.acquire("RiveView")

        owner.lifecycleScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastFrameTime = 0.nanoseconds
                while (isActive) {
                    var shouldBreak = false
                    val deltaTime = withFrameNanosChoreographer { frameTimeNs ->
                        val frameTime = frameTimeNs.nanoseconds
                        (if (lastFrameTime == 0.nanoseconds) 0.nanoseconds else frameTime - lastFrameTime).also {
                            lastFrameTime = frameTime
                        }
                    }

                    traceSection("Rive/Frame") {
                        val file = riveFile ?: run {
                            shouldBreak = true
                            return@traceSection
                        }
                        val art = artboardHandle ?: run {
                            shouldBreak = true
                            return@traceSection
                        }
                        val sm = stateMachineHandle ?: run {
                            shouldBreak = true
                            return@traceSection
                        }
                        val rs = riveSurface ?: run {
                            shouldBreak = true
                            return@traceSection
                        }
                        val cq = file.riveWorker

                        traceSection("Rive/Frame/Advance") {
                            cq.advanceStateMachine(sm, deltaTime)
                        }

                        traceSection("Rive/Frame/Draw") {
                            cq.draw(art, sm, rs, Fit.Contain())
                        }
                    }
                    if (shouldBreak) break
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        riveFile?.riveWorker?.release("RiveView", "Detached from window")
    }

    val textureView = TextureView(context).apply {
        layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                newSurfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                this@RiveView.surfaceTexture = newSurfaceTexture
                surfaceWidth = width
                surfaceHeight = height
                riveFile?.let { file ->
                    riveSurface = createRiveSurface(file, newSurfaceTexture)
                }
            }

            override fun onSurfaceTextureDestroyed(destroyedSurfaceTexture: SurfaceTexture): Boolean {
                riveSurface = null
                // False here means that we are responsible for destroying the surface texture.
                // This happens when the RiveSurface is closed.
                return false
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
            artboard?.artboardHandle ?: file.riveWorker.createDefaultArtboard(file.fileHandle)
        stateMachineHandle = if (stateMachineName != null)
            file.riveWorker.createStateMachineByName(artboardHandle!!, stateMachineName)
        else
            file.riveWorker.createDefaultStateMachine(artboardHandle!!)

        if (surfaceTexture != null && riveSurface == null) {
            riveSurface = createRiveSurface(file, surfaceTexture!!)
        }
    }

    private fun createRiveSurface(file: RiveFile, surfaceTexture: SurfaceTexture): RiveSurface {
        return file.riveWorker.createRiveSurface(SurfaceTextureSurface(surfaceTexture))
    }
}
