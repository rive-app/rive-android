package app.rive

/**
 * Marks experimental Rive accessibility semantics APIs.
 *
 * These APIs may change incompatibly as authored semantics and their platform integrations mature.
 */
@RequiresOptIn(
    message = "Rive accessibility semantics are experimental and may change incompatibly.",
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
annotation class ExperimentalRiveSemantics
