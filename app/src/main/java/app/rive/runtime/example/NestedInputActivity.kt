package app.rive.runtime.example

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView

class NestedInputActivity : AppCompatActivity() {

    private val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.nested_input)
    }

    fun onSetInput(view: View) {
        if (view is RadioButton && view.isChecked) {
            // Check which radio button was clicked
            when (view.getId()) {

                R.id.outer_circle_on ->
                    animationView.setBooleanStateAtPath("CircleOuterState", true, "CircleOuter")
                R.id.outer_circle_off ->
                    animationView.setBooleanStateAtPath("CircleOuterState", false, "CircleOuter")
                R.id.inner_circle_on ->
                    animationView.setBooleanStateAtPath("CircleInnerState", true, "CircleOuter/CircleInner")
                R.id.inner_circle_off ->
                    animationView.setBooleanStateAtPath("CircleInnerState", false, "CircleOuter/CircleInner")

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.nested_input)
    }
}
