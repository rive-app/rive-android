package app.rive.runtime.example

import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.add
import androidx.fragment.app.commit

class ViewStubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_stub)

        val viewStub = findViewById<ViewStub>(R.id.rive_viewstub_container)
        viewStub.setOnInflateListener { _, _ ->
            // Instantiate our RiveFragment
            if (savedInstanceState == null) {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    add<TomMorelloController>(
                        R.id.rive_viewstub_fragment_container,
                        args = bundleOf(RIVE_FRAGMENT_ARG_RES_ID to R.raw.tom_morello)
                    )
                }
            }
        }

        val showButton = findViewById<Button>(R.id.showButtonForViewStub)
        val hideButton = findViewById<Button>(R.id.hideButtonForViewStub)

        showButton.setOnClickListener {
            viewStub.visibility = View.VISIBLE
        }

        hideButton.setOnClickListener {
            viewStub.visibility = View.GONE
        }

    }
}

class TomMorelloController : RiveFragment("Tom Morello") {
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState == null) {
            riveView.play("State Machine 1", isStateMachine = true)
            riveView.setBooleanState("State Machine 1", "Rage", true)
        }
    }
}