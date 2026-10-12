package app.rive

/**
 * Marks entry points that enable GPU Canvas content, which draws through a deferred renderer.
 *
 * The deferred renderer does not improve rendering performance yet. These entry points will be
 * removed once every Rive worker can render GPU Canvas content. Consumers must opt in explicitly
 * to acknowledge the temporary API before using these declarations.
 */
@RequiresOptIn(
    message = "GPU Canvas support is experimental, and these entry points are temporary.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
annotation class ExperimentalDeferredRendering
