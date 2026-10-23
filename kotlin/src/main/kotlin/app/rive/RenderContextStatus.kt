package app.rive

/**
 * Whether a Rive worker can render.
 *
 * More values may be added in later releases. Compare against the value you need, for example
 * `status == RenderContextStatus.Lost`, or add an `else` branch to a `when`.
 */
enum class RenderContextStatus {
    /** The worker can render its Rive instances. */
    Ready,

    /**
     * The GPU device or context behind the worker was lost, so its Rive instances can no longer
     * render. Release the worker, then create a new one and load your files again.
     */
    Lost,
}
