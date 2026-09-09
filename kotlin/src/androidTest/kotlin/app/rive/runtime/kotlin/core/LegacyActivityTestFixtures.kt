package app.rive.runtime.kotlin.core

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import app.rive.runtime.kotlin.test.R

/** Hosts a legacy Rive view inflated from an Android test resource. */
class LegacyRiveTestActivity : ComponentActivity() {
    /** Inflates the legacy Rive view used by real-Activity lifecycle tests. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.rive_activity_test)
    }
}

/** Hosts legacy views built programmatically by instrumentation tests. */
class LegacyEmptyTestActivity : ComponentActivity() {
    /** Container to which tests add their views. */
    lateinit var container: FrameLayout
        private set

    /** Creates an empty full-screen container for the test. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(container)
    }
}
