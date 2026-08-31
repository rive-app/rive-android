package app.rive

/**
 * Marks experimental Rive global view model APIs.
 *
 * These APIs may change incompatibly as global view model support matures.
 */
@RequiresOptIn(
    message = "Rive global view models are experimental and may change incompatibly.",
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
annotation class ExperimentalRiveGlobalViewModels
