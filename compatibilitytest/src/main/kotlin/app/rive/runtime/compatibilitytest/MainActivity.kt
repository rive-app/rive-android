package app.rive.runtime.compatibilitytest

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive

typealias RiveSetupFunction = (riveView: RiveAnimationView) -> Unit

data class AnimationTest(
    val name: String,
    val resource: Int,
    val width: Int,
    val height: Int,
    // Optional function to perform actions on the view before draw.
    val setup: RiveSetupFunction? = null,
)

data class RendererSetup(val name: String, val type: RendererType)

val tests = listOf(
    AnimationTest("mesh", R.raw.dwarf, 860, 540),
    AnimationTest("text", R.raw.text, 860, 540),
    AnimationTest("vector", R.raw.vector, 860, 540),
    AnimationTest("fallback fonts", R.raw.fallback_fonts, 860, 540) {
        it.setTextRunValue("Name", "txt")
    },
)
val rendererSetups = listOf(
    RendererSetup("canvas", RendererType.Canvas),
    RendererSetup("rive", RendererType.Rive)
)

// Storage Permissions
private const val REQUEST_EXTERNAL_STORAGE = 1
private val PERMISSIONS_STORAGE = arrayOf(
    android.Manifest.permission.READ_EXTERNAL_STORAGE,
    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
)

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(applicationContext)
        setContentView(R.layout.main)

        val buttonLayout = findViewById<LinearLayout>(R.id.buttonContainer)
        rendererSetups.forEach { setup ->
            val label = TextView(this)
            label.text = "Check - ${setup.name}"
            buttonLayout.addView(label)

            tests.forEach { test ->
                addButton(buttonLayout, setup, test)
            }
        }

        val permission =
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                this,
                PERMISSIONS_STORAGE,
                REQUEST_EXTERNAL_STORAGE
            )
        }
    }

    private fun addButton(buttonLayout: LinearLayout, setup: RendererSetup, test: AnimationTest) {
        val that = this
        val button = Button(this)
        button.text = "${setup.name} - ${test.name}"
        button.setOnClickListener {
            that.setView(setup, test)
        }
        buttonLayout.addView(button)
    }

    private fun setView(setup: RendererSetup, test: AnimationTest) {

        val builder = RiveAnimationView.Builder(context = applicationContext).apply {
            this.setFit(Fit.CONTAIN)
            this.setRendererType(setup.type)
            this.setAutoplay(false)
            this.setResource(test.resource)
        }

        val view = CallbackRiveAnimationView(builder)
        view.drawCallback = {
            Log.d("BitmapExtractor", "Draw callback called for  ${test.name}")
            extractor.extractView(this, view, "${setup.name}_${test.name}")
        }
        view.layoutParams = ViewGroup.LayoutParams(test.width, test.height)
        val displayLayout = findViewById<LinearLayout>(R.id.viewContainer)
        displayLayout.removeAllViews()
        displayLayout.addView(view)
        // Setup the text after it's been added and init'd
        test.setup?.invoke(view)
    }
}
