package app.rive

/**
 * Marks low-level primitives that Rive's own views are built from, published for runtimes and
 * frameworks that integrate Rive into their own view systems.
 *
 * These are not part of the supported app-facing API. Unlike the experimental markers, they are not
 * on a path to stabilization: they follow the needs of Rive's internal rendering and may change
 * or be removed in any release. Apps should use `Rive`, `RiveView`, or the other public entry
 * points instead.
 */
@RequiresOptIn(
    message = "This is a low-level Rive integration API with no compatibility or support " +
        "guarantees. Apps should use the public Rive views instead.",
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
annotation class RiveImplementationApi
