package app.rive.runtime.example

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveGLSurfaceView

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
    private val secondGlViewId by lazy(LazyThreadSafetyMode.NONE) { R.id.rive_gl_surface_view_2 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.opengl)
        addSpinner()
        loadResourceFromSpinner(0)
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

    private fun loadResourceFromSpinner(index: Int, viewId: Int = glViewId) {
        val currentGlSurfaceView = findViewById<RiveGLSurfaceView?>(viewId)
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
