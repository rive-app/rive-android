package app.rive.runtime.example.utils

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.LayoutRes
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

/**
 * With SDK 36+, edge-to-edge is the default behavior. Android Views must now account for insets. To
 * minimize boilerplate, this function captures the necessary steps to apply a margin for the system
 * bars, i.e. the status bar (top) and navigation bar (bottom).
 *
 * Without this, content will draw under these areas.
 *
 * See: https://developer.android.com/develop/ui/views/layout/edge-to-edge#system-bars-insets
 *
 * @param view The view to apply the inset margins to.
 */
fun applyEdgeToEdgeMargin(view: View) {
    setOnApplyWindowInsetsListener(view) { v, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = insets.top
            leftMargin = insets.left
            bottomMargin = insets.bottom
            rightMargin = insets.right
        }
        WindowInsetsCompat.CONSUMED
    }
    view.requestApplyInsets()
}

/** Further reduce boilerplate with an extension function to apply the margins to the root view. */
fun ComponentActivity.applyEdgeToEdgeMarginToRoot() {
    // Find the view assigned by `setContentView`
    val content = findViewById<ViewGroup>(android.R.id.content)
    val root = content.getChildAt(0) ?: return
    applyEdgeToEdgeMargin(root)
}

/**
 * Captures the boilerplate required to enable edge-to-edge on an activity, including the operation
 * of setting the content.
 *
 * @param layoutResID The layout to set as the content.
 */
fun ComponentActivity.setEdgeToEdgeContent(@LayoutRes layoutResID: Int) {
    enableEdgeToEdge()
    setContentView(layoutResID)
    applyEdgeToEdgeMarginToRoot()
}

/**
 * Captures the boilerplate required to enable edge-to-edge on an activity, including the operation
 * of setting the content.
 *
 * @param view The view to set as the content.
 */
fun ComponentActivity.setEdgeToEdgeContent(view: View) {
    enableEdgeToEdge()
    setContentView(view)
    applyEdgeToEdgeMarginToRoot()
}
