package app.rive.runtime.example

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.activity.ComponentActivity
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import app.rive.runtime.kotlin.RiveAnimationView

class NestedInputActivity : ComponentActivity() {

    private val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.nested_input)
    }

    fun onSetInput(view: View) {
        if (view is RadioButton && view.isChecked) {
            // Check which radio button was clicked
            when (view.id) {

                R.id.outer_circle_on ->
                    animationView.setBooleanStateAtPath("CircleOuterState", true, "CircleOuter")

                R.id.outer_circle_off ->
                    animationView.setBooleanStateAtPath("CircleOuterState", false, "CircleOuter")

                R.id.inner_circle_on ->
                    animationView.setBooleanStateAtPath(
                        "CircleInnerState",
                        true,
                        "CircleOuter/CircleInner"
                    )

                R.id.inner_circle_off ->
                    animationView.setBooleanStateAtPath(
                        "CircleInnerState",
                        false,
                        "CircleOuter/CircleInner"
                    )

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeContent(R.layout.nested_input)
    }
}
