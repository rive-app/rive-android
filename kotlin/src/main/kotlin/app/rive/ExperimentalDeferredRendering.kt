package app.rive

/**
 * Marks entry points for deferred rendering, which records draws into a session and replays them
 * synchronously, enabling GPU canvas (ore) content.
 *
 * Deferred rendering is on track to become the only rendering mode, at which point these entry
 * points disappear and the stable API defers implicitly. Consumers must opt in explicitly to
 * acknowledge the temporary API before using these declarations.
 */
@RequiresOptIn(
    message = "Deferred rendering is experimental scaffolding and will become the default; " +
        "these entry points are temporary.",
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
