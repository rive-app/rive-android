package app.rive.runtime.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import app.rive.runtime.kotlin.RiveAnimationView

class FrameActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_frame)

        // Don't do anything if restoring a previous state.
        if (savedInstanceState != null) {
            return
        }

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(R.id.frame_fragment_container, TextFragment())
        }

        findViewById<Button>(R.id.swap_fragment).setOnClickListener {
            val currentFragment =
                supportFragmentManager.findFragmentById(R.id.frame_fragment_container)
            supportFragmentManager.commit {
                replace(
                    R.id.frame_fragment_container,
                    if (currentFragment is TextFragment) RiveSimpleFragment() else TextFragment()
                )
            }
        }
    }
}

class TextFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_text, container, false)
    }
}

open class RiveSimpleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val riveFragmentContainerView =
            inflater.inflate(R.layout.fragment_rive, container, false)
        val riveView =
            riveFragmentContainerView.findViewById<RiveAnimationView>(R.id.rive_view_fragment)
        riveView.layoutParams.apply {
            width = 300
            height = 300
        }
        riveView.setRiveResource(R.raw.basketball)

        return riveFragmentContainerView
    }
}
