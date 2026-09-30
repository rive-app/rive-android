package app.rive

/**
 * Marks experimental Rive focus management APIs.
 *
 * These APIs may change incompatibly while keyboard traversal and its platform integrations mature.
 * Directional navigation and key or text input forwarding are not covered yet, and adding them is
 * expected to extend this surface.
 */
@RequiresOptIn(
    message = "Rive focus management is experimental and may change incompatibly.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
)
annotation class ExperimentalRiveFocus
