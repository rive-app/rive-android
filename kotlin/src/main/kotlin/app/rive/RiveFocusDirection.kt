package app.rive

/**
 * The direction to move Rive focus in.
 *
 * [Left], [Right], [Up], and [Down] move from the focused element, so they do nothing while no
 * element holds focus. Use [Next] or [Previous] to move focus into the Rive instance.
 *
 * @param nativeMapping A code the JNI layer maps to a core focus move. The core runtime does not use
 *    these values, so the mapping only exists in JNI.
 */
@ExperimentalRiveFocus
enum class RiveFocusDirection(internal val nativeMapping: Int) {
    /** The next focusable element in traversal order. */
    Next(0),

    /** The previous focusable element in traversal order. */
    Previous(1),

    /** The nearest focusable element to the left. */
    Left(2),

    /** The nearest focusable element to the right. */
    Right(3),

    /** The nearest focusable element above. */
    Up(4),

    /** The nearest focusable element below. */
    Down(5),
}
