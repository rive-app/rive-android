package app.rive.runtime.example

import android.content.Context
import android.opengl.GLES20
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGLActivity : AppCompatActivity() {
    private lateinit var glView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        print("onCreate() GL Activity!")
        glView = RiveGLSurfaceView(this)
        setContentView(glView)
    }
}

class RiveGLSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer: RiveGLRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = RiveGLRenderer()

        setRenderer(renderer)
//        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }
}

class RiveGLRenderer : GLSurfaceView.Renderer {
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        println("surface created!")
        Log.d(javaClass.simpleName,"Surface created?")
        GLES20.glClearColor(1.0f, 0.7f, 0.2f, 1.0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        println("Rendering something?")
//        Log.d(javaClass.simpleName,"Rendering something?")
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }
}