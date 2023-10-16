package app.rive.runtime.example

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity


class SingleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isRiveRenderer = intent.getStringExtra("renderer") == "Rive"
        setContentView(
            if (isRiveRenderer)
                R.layout.single_rive_renderer
            else
                R.layout.single
        )
    }
}

class EmptyActivity : AppCompatActivity() {

    lateinit var container: FrameLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create a new FrameLayout object and add it.
        FrameLayout(this).let {
            container = it
            it.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setContentView(it)
        }


    }
}