package app.rive

import android.view.View
import androidx.annotation.MainThread

/**
 * Describes whether and when a view hosting a Rive instance can receive Android input focus.
 *
 * Apply it to the host view with [applyTo]. See [RiveFocusMode.Automatic] for how Android switches
 * between keyboard and touch input modes.
 *
 * @param isFocusable The value for [View.setFocusable].
 * @param isFocusableInTouchMode The value for [View.setFocusableInTouchMode].
 */
@ExperimentalRiveFocus
enum class RiveFocusability(val isFocusable: Boolean, val isFocusableInTouchMode: Boolean) {
    /** The view can't receive Android input focus. */
    None(isFocusable = false, isFocusableInTouchMode = false),

    /** The view can receive Android input focus in keyboard input mode only. */
    Keyboard(isFocusable = true, isFocusableInTouchMode = false),

    /** The view can receive Android input focus in either input mode, including from a tap. */
    KeyboardAndTouch(isFocusable = true, isFocusableInTouchMode = true),
    ;

    /**
     * Applies this focusability to [view].
     *
     * Also clears focus the view holds in touch mode once it can no longer receive focus there.
     * Android keeps that focus when only [View.setFocusableInTouchMode] changes, such as when the
     * mode switches from [RiveFocusMode.On] to [RiveFocusMode.Automatic].
     *
     * @param view The view hosting the Rive instance.
     */
    @MainThread
    fun applyTo(view: View) {
        // Set before focusable: making the view focusable offers it focus, which fails in touch
        // mode unless this is already set.
        view.isFocusableInTouchMode = isFocusableInTouchMode
        view.isFocusable = isFocusable
        if (view.isFocused && view.isInTouchMode && !isFocusableInTouchMode) {
            view.clearFocus()
        }
    }
}
