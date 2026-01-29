package app.rive.runtime.example

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import app.rive.runtime.example.utils.setEdgeToEdgeContent

class SingleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setEdgeToEdgeContent(R.layout.single)
    }
}

class EmptyActivity : ComponentActivity() {
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
            setEdgeToEdgeContent(it)
        }
    }
}
