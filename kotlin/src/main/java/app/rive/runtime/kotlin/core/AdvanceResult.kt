package app.rive.runtime.kotlin.core

/**
 * Represents the outcome of advancing a [LinearAnimationInstance].
 */
enum class AdvanceResult {
    /** The animation advanced but did not loop or finish. */
    ADVANCED,
    /** The animation reached its end in ONESHOT mode and stopped. */
    ONESHOT,
    /** The animation looped back to the beginning in LOOP mode. */
    LOOP,
    /** The animation reached an end and reversed direction in PINGPONG mode. */
    PINGPONG,
    /** The animation was already finished and did not advance effectively. */
    NONE;
}
