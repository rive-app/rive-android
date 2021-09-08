package app.rive.runtime.example

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.RendererOpenGL
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGLActivity : AppCompatActivity() {
    private var glSurfaceView: RiveGLSurfaceView? = null

    // Values for the Dropdown
    private val animationResources = listOf(
        R.raw.artboard_animations,
        R.raw.basketball,
        R.raw.clipping,
        R.raw.constrained,
        R.raw.explorer,
        R.raw.f22,
        R.raw.flux_capacitor,
        R.raw.loopy,
        R.raw.mascot,
        R.raw.neostream,
        R.raw.off_road_car_blog,
        R.raw.progress,
        R.raw.pull,
        R.raw.skills,
        R.raw.trailblaze,
        R.raw.two_bone_ik,
        R.raw.ui_swipe_left_to_delete,
    )

    private val containerView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<LinearLayout>(R.id.container)
    }
    private val glViewId by lazy(LazyThreadSafetyMode.NONE) { R.id.rive_gl_surface_view }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.opengl)
        addSpinner()
    }

    private fun addSpinner() {
        val spinner = Spinner(this)
        spinner.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
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
                loadResourceFromSpinner(index)
            }

            override fun onNothingSelected(arg0: AdapterView<*>?) {}
        }

        containerView.addView(spinner)
    }


    private fun loadResourceFromSpinner(index: Int) {

        val currentGlSurfaceView = findViewById<RiveGLSurfaceView?>(glViewId)
        if (currentGlSurfaceView != null) {
            containerView.removeView(currentGlSurfaceView)
        }
        val res = animationResources[index]
        val fileBytes = resources.openRawResource(res).readBytes()
        initGLSurfaceView(fileBytes)
    }

    private fun initGLSurfaceView(fileBytes: ByteArray) {
        glSurfaceView = RiveGLSurfaceView(this, fileBytes)
        val density = resources.displayMetrics.density
        glSurfaceView!!.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (366 * density).toInt(),
        )

        containerView.addView(glSurfaceView, 0)
        glSurfaceView!!.id = glViewId
    }
}

class RiveGLSurfaceView(context: Context, fileBytes: ByteArray, attrs: AttributeSet? = null) :
    GLSurfaceView(context, attrs) {
    val renderer: RiveGLRenderer
    var rendererGL = RendererOpenGL()

    init {
        // Init GL context.
        // luigi: specifically requesting v3 here as our shaders are written for GL 3,
        //  we could totally tweak those for es2. we actually need a better abstraction there
        //  for different platforms in general
        setEGLContextClientVersion(3)
        renderer = RiveGLRenderer(rendererGL, fileBytes)
        // Set up the stencil buffer.
        setEGLConfigChooser(8, 8, 8, 8, 0, 8)
        setRenderer(renderer)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rendererGL.cleanup()
    }
}

class RiveGLRenderer(val rendererGL: RendererOpenGL, val fileBytes: ByteArray) :
    GLSurfaceView.Renderer {
    private var lastTime: Long = 0
    var isPlaying = true

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Init file after GL init.
        rendererGL.initializeGL()
        rendererGL.initFile(fileBytes)
        lastTime = System.currentTimeMillis()
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