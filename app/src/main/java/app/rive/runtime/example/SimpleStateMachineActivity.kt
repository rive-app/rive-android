package app.rive.runtime.example

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.SMINumber

class SimpleStateMachineActivity : AppCompatActivity() {

    private val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.simple_state_machine)
    }

    val levelInput: SMINumber
        get() = animationView.playingStateMachines.first().input("Level") as SMINumber

    fun onLevelSelect(view: View) {
        if (view is RadioButton && view.isChecked) {
            // Check which radio button was clicked
            when (view.getId()) {
                R.id.level_beginner ->
                    levelInput.value = 0f
                R.id.level_intermediate ->
                    levelInput.value = 1f
                R.id.level_advanced ->
                    levelInput.value = 2f
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init()
        setContentView(R.layout.simple_state_machine)
    }

    override fun onDestroy() {
        super.onDestroy()
        animationView.destroy()
    }
}
