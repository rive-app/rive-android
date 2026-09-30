package app.rive

/**
 * A snapshot of the focus state Rive holds for one state machine.
 *
 * @param hasFocus Whether any element inside the graphic currently holds Rive focus.
 * @param expectsKeyboardInput Whether the focused element accepts key and text input, such as a
 *    text input or a focus target with registered key-input listeners. False when nothing is
 *    focused.
 */
@ExperimentalRiveFocus
data class RiveFocusState(val hasFocus: Boolean = false, val expectsKeyboardInput: Boolean = false)
