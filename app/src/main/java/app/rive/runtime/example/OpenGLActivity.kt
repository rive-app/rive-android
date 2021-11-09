package app.rive.runtime.example

import android.content.Context
import android.opengl.GLES20
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.opengl.GLSurfaceView
import android.util.Log
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.RendererOpenGL
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGLActivity : AppCompatActivity() {
    private var rendererGL = RendererOpenGL()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bytes = resources.openRawResource(R.raw.artboard_animations).readBytes()
        val glView = RiveGLSurfaceView(this, rendererGL, bytes)
        setContentView(glView)
    }

    override fun onDestroy() {
        super.onDestroy()
        rendererGL.cleanup()
    }
}

class RiveGLSurfaceView(
    context: Context,
    private val rendererGL: RendererOpenGL,
    fileBytes: ByteArray
) : GLSurfaceView(context) {
    private val renderer: RiveGLRenderer

    init {
        // Init GL context.
        // luigi: specifically requesting v3 here as our shaders are written for GL 3, we could totally tweak those for es2. we actually need a better abstraction there for different platforms in general
        setEGLContextClientVersion(3) 

        renderer = RiveGLRenderer(rendererGL, fileBytes)
        setRenderer(renderer)
//        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }
}

class RiveGLRenderer(private val rendererGL: RendererOpenGL, private val fileBytes: ByteArray) :
    GLSurfaceView.Renderer {
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Init file after GL init.
        rendererGL.initializeGL()
        rendererGL.initFile(fileBytes)
        GLES20.glClearColor(1.0f, 0.7f, 0.2f, 1.0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        rendererGL.setViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        rendererGL.draw()
    }
}