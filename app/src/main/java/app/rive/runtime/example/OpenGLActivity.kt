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
import app.rive.runtime.kotlin.core.RendererSkia
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
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
        val adapter = ArrayAdapter(
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
                arg3: Long,
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
        glSurfaceView = RiveGLSurfaceView(this, null, fileBytes)
        val density = resources.displayMetrics.density
        glSurfaceView!!.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (366 * density).toInt(),
        )

        containerView.addView(glSurfaceView, 0)
        glSurfaceView!!.id = glViewId
    }
}

class RiveGLSurfaceView(context: Context, attrs: AttributeSet? = null, fileBytes: ByteArray) :
    GLSurfaceView(context, attrs) {
    private val glRenderer: RiveGLRenderer
//    private var renderer = RendererOpenGL()
    private var riveRenderer = RendererSkia()

    init {
        // Init GL context.
        // luigi: specifically requesting v3 here as our shaders are written for GL 3,
        //  we could totally tweak those for es2. we actually need a better abstraction there
        //  for different platforms in general
        setEGLContextClientVersion(3)
        glRenderer = RiveGLRenderer(riveRenderer, fileBytes)
        // setEGLConfigChooser(RiveConfigChooser())
        // Set up the stencil buffer.
        // setEGLConfigChooser(8, 8, 8, 8, 0, 8)
        setRenderer(glRenderer)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        riveRenderer.cleanup()
    }

    private class RiveConfigChooser : EGLConfigChooser {
        companion object ChooserDefaults {
            private const val MAX_CONFIGS = 128
            private const val RED_MIN_SIZE = 8
            private const val GREEN_MIN_SIZE = 8
            private const val BLUE_MIN_SIZE = 8

            // We need a configuration with the stencil buffer.
            private const val STENCIL_MIN_SIZE = 8
            private val refValue = IntArray(1)
        }

        override fun chooseConfig(egl: EGL10?, display: EGLDisplay?): EGLConfig? {
            val configArray = arrayOfNulls<EGLConfig>(MAX_CONFIGS)
            val configCount = IntArray(1)
            egl?.eglGetConfigs(display, configArray, MAX_CONFIGS, configCount)
            return if (configCount.first() <= 0) {
                throw IllegalArgumentException("No Config match our defaults?")
            } else {
                findConfig(egl!!, display!!, configArray.copyOfRange(0, configCount.first()))
            }

        }

        private fun findConfig(
            egl: EGL10,
            display: EGLDisplay,
            configArray: Array<EGLConfig?>,
        ): EGLConfig? {

            var candidate: EGLConfig? = null
            // MSAA is optional as it might not be supported by the device.
            var lastSamples = 0

            for (config in configArray) {
                val stencil = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE)
                val red = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE)
                val green = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE)
                val blue = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE)
                val samples = findConfigAttrib(egl, display, config, EGL10.EGL_SAMPLES)
                if (
                    red == RED_MIN_SIZE &&
                    green == GREEN_MIN_SIZE &&
                    blue == BLUE_MIN_SIZE &&
                    stencil == STENCIL_MIN_SIZE &&
                    samples >= lastSamples
                ) {
                    lastSamples = samples
                    candidate = config
                }
            }

            return candidate
        }

        private fun findConfigAttrib(
            egl: EGL10,
            display: EGLDisplay,
            config: EGLConfig?,
            attribute: Int,
        ): Int {
            if (egl.eglGetConfigAttrib(display, config, attribute, refValue)) {
                return refValue.first()
            }
            return 0
        }
    }
}

class RiveGLRenderer(private val riveRenderer: RendererSkia, private val fileBytes: ByteArray) :
    GLSurfaceView.Renderer {
    private var lastTime: Long = 0
    private var isPlaying = true

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        riveRenderer.initializeSkia()
        // Init file after GL init.
        riveRenderer.initFile(fileBytes)
        lastTime = System.currentTimeMillis()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        riveRenderer.setViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!isPlaying) return
        val time = System.currentTimeMillis()
        val elapsed = time - lastTime
        lastTime = time
        riveRenderer.draw(elapsed / 1000.0f)
    }
}