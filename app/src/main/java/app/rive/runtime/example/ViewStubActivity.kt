package app.rive.runtime.example

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.add
import androidx.fragment.app.commit
import app.rive.runtime.example.databinding.ActivityViewStubBinding

class ViewStubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewStubBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewStubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val viewStub = binding.viewstubRiveContainer
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

        binding.viewstubShowButton.setOnClickListener {
            viewStub.visibility = View.VISIBLE
        }

        binding.viewstubHideButton.setOnClickListener {
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