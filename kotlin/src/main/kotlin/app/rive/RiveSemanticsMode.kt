package app.rive

/**
 * Controls whether a [Rive] composable exposes Rive-authored Android accessibility semantics.
 */
enum class RiveSemanticsMode {
    /** Do not expose Rive-authored accessibility semantics. */
    Off,

    /** Always expose Rive-authored accessibility semantics. */
    @ExperimentalRiveSemantics
    On,

    /**
     * Expose Rive-authored accessibility semantics while Android accessibility is enabled.
     *
     * Android reports this state when at least one accessibility service is enabled. It is not a
     * TalkBack-specific signal and may be true for other accessibility services.
     */
    @ExperimentalRiveSemantics
    Automatic,
}
