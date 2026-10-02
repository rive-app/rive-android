package app.rive

/** The direction to move Rive focus in. */
@ExperimentalRiveFocus
enum class RiveFocusDirection {
    /** The next focusable element in traversal order. */
    Next,

    /** The previous focusable element in traversal order. */
    Previous,
}
