package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * Controls whether the Rive surface can receive Android input focus.
 *
 * Enabling focus support requires opting into [ExperimentalRiveFocus].
 */
enum class RiveFocusMode {
    /** The Rive surface never receives Android input focus. */
    Off,

    /**
     * Allows the Rive surface to receive Android input focus whenever the Rive instance has
     * focusable content.
     *
     * An instance whose [StateMachine.hasFocusNodes] reports false stays out of Android focus
     * navigation, and joins it if focusable content appears later.
     */
    @ExperimentalRiveFocus
    On,

    /**
     * Like [On], but only while the system is in keyboard input mode; disabled in touch mode.
     *
     * Android switches between these modes as the user interacts with the device. Keyboard
     * navigation, such as pressing Tab, normally enters keyboard mode, and touching the screen
     * enters touch mode. With [Automatic], whether the surface can receive Android input focus
     * updates whenever the input mode changes.
     *
     * This follows the current interaction mode, not whether a physical keyboard is connected or
     * the on-screen keyboard is visible. Touch and pointer interaction with the Rive instance
     * remains available in either mode.
     *
     * @see androidx.compose.ui.platform.LocalInputModeManager
     * @see android.view.View.isInTouchMode
     */
    @ExperimentalRiveFocus
    Automatic,
    ;

    /**
     * Resolves this mode against the current input mode.
     *
     * @param keyboardActive Whether the system is in keyboard input mode.
     * @return Whether the Rive surface can receive Android input focus.
     */
    internal fun isEnabled(keyboardActive: Boolean): Boolean = when (this) {
        Off -> false
        On -> true
        Automatic -> keyboardActive
    }

    /**
     * Resolves this mode to the [RiveFocusability] of a view hosting the Rive instance.
     *
     * @param hasFocusNodes Whether the Rive instance has focusable content, or null before the
     *    first answer, which counts as focusable content.
     * @return [RiveFocusability.None] without focusable content, otherwise the mode's focusability.
     */
    internal fun focusability(hasFocusNodes: Boolean?): RiveFocusability = when {
        hasFocusNodes == false -> RiveFocusability.None

        else -> when (this) {
            Off -> RiveFocusability.None
            On -> RiveFocusability.KeyboardAndTouch
            Automatic -> RiveFocusability.Keyboard
        }
    }
}

/**
 * Resolves whether the Rive surface can currently receive Android input focus.
 *
 * @param mode The focus mode to resolve.
 * @return Whether the Rive surface can receive Android input focus.
 */
@Composable
internal fun rememberRiveFocusEnabled(mode: RiveFocusMode): Boolean {
    val inputMode = LocalInputModeManager.current.inputMode
    return mode.isEnabled(keyboardActive = inputMode == InputMode.Keyboard)
}
