package app.rive

/** A snapshot of the focus state Rive holds for one state machine. */
@ExperimentalRiveFocus
sealed interface RiveFocusState {
    /** No element inside the Rive instance holds focus. */
    data object Unfocused : RiveFocusState

    /**
     * An element inside the Rive instance holds focus.
     *
     * @param expectsKeyboardInput Whether the focused element accepts key and text input, such as
     *    a text input or a focus target with registered key-input listeners.
     */
    data class Focused(val expectsKeyboardInput: Boolean) : RiveFocusState
}

/**
 * Builds a [RiveFocusState] from the flags the command server reports.
 *
 * @param hasFocus Whether an element holds focus.
 * @param expectsKeyboardInput Whether the focused element accepts key and text input. Ignored when
 *    nothing holds focus.
 */
internal fun riveFocusStateOf(hasFocus: Boolean, expectsKeyboardInput: Boolean): RiveFocusState =
    if (hasFocus) RiveFocusState.Focused(expectsKeyboardInput) else RiveFocusState.Unfocused
