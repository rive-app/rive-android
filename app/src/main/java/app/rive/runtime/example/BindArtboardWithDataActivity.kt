package app.rive.runtime.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import app.rive.runtime.kotlin.RiveAnimationView
import kotlinx.coroutines.launch

class BindArtboardWithDataActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rive = RiveAnimationView(this)
        rive.setRiveResource(
            R.raw.data_bind_test_impl,
            "Bindable Artboard Host",
            autoBind = true,
            autoplay = true
        )

        setEdgeToEdgeContent(rive)

        val file = rive.file!!
        val vmi = file.getViewModelByName("Test Bound Artboard Child VM")
            .createInstanceFromName("Override")
        val bindableArtboard = file.createBindableArtboardByName("Bindable Artboard With VM", vmi)

        lifecycleScope.launch {
            vmi.getStringProperty("Echo String").valueFlow.collect {
                Log.i("BindArtboard", "New echo string: $it")
            }
        }

        val artboardProperty =
            rive.controller.stateMachines.first().viewModelInstance!!.getArtboardProperty("Child Artboard")

        artboardProperty.set(bindableArtboard)
        bindableArtboard.release() // Release the reference we hold from creation
    }
}
