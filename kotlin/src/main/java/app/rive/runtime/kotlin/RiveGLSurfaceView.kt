package app.rive.runtime.kotlin

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.LinearAnimationInstance
import app.rive.runtime.kotlin.renderers.RendererSkia
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class RiveGLSurfaceView(context: Context, attrs: AttributeSet? = null, fileBytes: ByteArray) :
    GLSurfaceView(context, attrs) {
    private val riveRenderer: RiveGLSurfaceViewRenderer

    init {
        // Init GL context.
        // luigi: specifically requesting v3 here as our shaders are written for GL 3,        //  we could totally tweak those for es2. we actually need a better abstraction there
        //  for different platforms in general
        setEGLContextClientVersion(3)
        riveRenderer = RiveGLSurfaceViewRenderer(fileBytes)
        setRenderer(riveRenderer)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        riveRenderer.cleanup()
    }
}

class RiveGLSurfaceViewRenderer(
    private val fileBytes: ByteArray
) :
    GLSurfaceView.Renderer {
    private val riveRenderer = RendererSkia()
    private var lastTime: Long = 0
    private var isPlaying = true
    private var artboard: Artboard? = null
    private lateinit var file: File

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        riveRenderer.initializeSkia()
        // Init file after GL init.
        initFile()
        lastTime = System.currentTimeMillis()
    }

    private fun initFile() {
        this.file = File(fileBytes)
        this.file.firstArtboard.getInstance().let {
            it.advance(0.0f)
            this.artboard = it
            val animation = it.firstAnimation
            val instance = LinearAnimationInstance(animation)
            it.playableInstances.add(instance)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        riveRenderer.setViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!isPlaying) {
            return
        }

        this.artboard?.let { artboard ->
            val time = System.currentTimeMillis()
            val elapsed = (time - lastTime) / 1000f
            lastTime = time
            if (artboard.advance(elapsed)) {
                riveRenderer.draw(artboard)
            }
        }
    }

    fun cleanup() {
        riveRenderer.cleanup()
    }
}