package app.rive.runtime.example

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.activity.ComponentActivity
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import app.rive.runtime.kotlin.RiveAnimationView

class SimpleStateMachineActivity : ComponentActivity() {

    private val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.simple_state_machine)
    }

    fun onLevelSelect(view: View) {
        if (view is RadioButton && view.isChecked) {
            // Check which radio button was clicked
            when (view.id) {

                R.id.level_beginner ->
                    animationView.setNumberState("Designer's Test", "Level", 0f)

                R.id.level_intermediate ->
                    animationView.setNumberState("Designer's Test", "Level", 1f)

                R.id.level_advanced ->
                    animationView.setNumberState("Designer's Test", "Level", 2f)

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeContent(R.layout.simple_state_machine)
    }
}
