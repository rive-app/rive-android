package app.rive.runtime.example

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.RendererOpenGL
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGLActivity : AppCompatActivity() {
    private val animationResources = listOf(
        R.raw.artboard_animations,
        R.raw.basketball,
        R.raw.clipping,
        R.raw.explorer,
        R.raw.f22,
        R.raw.flux_capacitor,
        R.raw.loopy,
        R.raw.mascot,
        R.raw.neostream,
        R.raw.off_road_car_blog,
        R.raw.progress,
        R.raw.pull,
        R.raw.rope,
        R.raw.skills,
        R.raw.trailblaze,
        R.raw.ui_swipe_left_to_delete,
        R.raw.vader,
        R.raw.wacky,
        R.raw.what_a_state,
        R.raw.two_bone_ik,
        R.raw.constrained,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.opengl)
        /*val spinner = findViewById<Spinner>(R.id.animations_spinner)
        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            animationResources.map { resources.getResourceName(it).split('/').last() }
        )
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                arg0: AdapterView<*>?,
                view: View?,
                index: Int,
                arg3: Long
            ) {
                loadResource(index)
            }

            override fun onNothingSelected(arg0: AdapterView<*>?) {}
        }*/
    }

    private val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveGLSurfaceView>(R.id.riveGLSurfaceView)
    }

    private fun loadResource(index: Int) {
        val res = animationResources[index]

        val fileBytes = resources.openRawResource(res).readBytes()
    }
}

class RiveGLSurfaceView(context: Context, attrs: AttributeSet? = null) :
    GLSurfaceView(context, attrs) {
    val renderer: RiveGLRenderer
    var rendererGL = RendererOpenGL()

    init {
        // Init GL context.
        // luigi: specifically requesting v3 here as our shaders are written for GL 3,
        //  we could totally tweak those for es2. we actually need a better abstraction there
        //  for different platforms in general
        setEGLContextClientVersion(3)
        renderer = RiveGLRenderer(rendererGL, context)
        setRenderer(renderer)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rendererGL.cleanup()
    }
}

class RiveGLRenderer(var rendererGL: RendererOpenGL, private val context: Context) :
    GLSurfaceView.Renderer {
    private var lastTime: Long = 0
    var isPlaying = true

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Init file after GL init.
        rendererGL.initializeGL()
        val fileBytes = context.resources.openRawResource(R.raw.artboard_animations).readBytes()
        rendererGL.initFile(fileBytes)
        lastTime = System.currentTimeMillis()
//        GLES20.glClearColor(1.0f, 0.7f, 0.2f, 1.0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        rendererGL.setViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!isPlaying) return
        val time = System.currentTimeMillis()
        val elapsed = time - lastTime
        lastTime = time
        rendererGL.draw(elapsed / 1000.0f)
    }
}