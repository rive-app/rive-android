package app.rive

import android.view.KeyEvent

/** The direction to move Rive focus in. */
@ExperimentalRiveFocus
enum class RiveFocusDirection {
    /** The next focusable element in traversal order. */
    Next,

    /** The previous focusable element in traversal order. */
    Previous,
}

/**
 * Resolves the traversal direction of a key event.
 *
 * @return [RiveFocusDirection.Next] for a `Tab` press, [RiveFocusDirection.Previous] for
 *    `Shift+Tab`, or null for any other key event.
 */
internal fun KeyEvent.traversalDirection(): RiveFocusDirection? {
    val isTabPress = action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_TAB
    if (!isTabPress) {
        return null
    }
    return if (isShiftPressed) RiveFocusDirection.Previous else RiveFocusDirection.Next
}
