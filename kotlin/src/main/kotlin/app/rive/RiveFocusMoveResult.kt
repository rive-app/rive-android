package app.rive

/**
 * The outcome of moving Rive focus.
 *
 * @param moved Whether focus moved to another element, including entering the Rive instance. False
 *    when there was nowhere to go, whether or not an element still holds focus.
 * @param focusState The focus state right after the move.
 */
@ExperimentalRiveFocus
data class RiveFocusMoveResult(val moved: Boolean, val focusState: RiveFocusState)
