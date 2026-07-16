package app.rive

import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private const val ONE_SECOND_IN_NANOS = 1_000_000_000.0

/**
 * Coarse wake headroom before the next target Rive frame time.
 *
 * Larger values wake earlier and reduce the risk of missing the intended platform frame callback,
 * but also reduce the battery savings from sleeping. Smaller values sleep longer, but can increase
 * cadence jitter if coroutine dispatch resumes too late to observe the intended frame. This value
 * is intentionally conservative and can be revisited with device measurements.
 */
private val DEFAULT_EARLY_WAKE = 4_000_000.nanoseconds

/**
 * Controls how often Rive advances and draws an active animation.
 *
 * This limits Rive frame production, not animation time. When capped, Rive still advances by the
 * real elapsed time between rendered frames.
 */
sealed interface RiveFrameRate {
    /**
     * Render on every frame callback received from the platform frame clock.
     */
    object Unbounded : RiveFrameRate

    /**
     * Render at no more than [framesPerSecond].
     *
     * @param framesPerSecond Maximum frames per second. Must be finite and greater than zero.
     * @throws IllegalArgumentException If [framesPerSecond] is not finite or is less than or equal
     *    to zero.
     */
    data class Capped(val framesPerSecond: Float) : RiveFrameRate {
        init {
            require(framesPerSecond.isFinite() && framesPerSecond > 0f) {
                "framesPerSecond must be finite and greater than zero."
            }
        }

        /**
         * Target time between accepted Rive frames, calculated as the reciprocal of
         * [framesPerSecond].
         */
        internal val period: Duration = (ONE_SECOND_IN_NANOS / framesPerSecond)
            .roundToLong()
            .coerceAtLeast(1L)
            .nanoseconds
    }
}

/**
 * Tracks frame-rate cap timing without owning platform frame callbacks.
 *
 * The caller should use [delayBeforeNextFrame] to sleep until close to the next target render
 * time, then still await the platform frame clock before rendering.
 *
 * @param frameRate Frame-rate policy used to decide which platform frame callbacks should produce
 *    Rive frames.
 * @param earlyWake Headroom subtracted from the coarse delay so callers wake before the target
 *    time and can still await the platform frame clock.
 */
internal class RiveFramePacer(
    frameRate: RiveFrameRate,
    private val earlyWake: Duration = DEFAULT_EARLY_WAKE
) {
    private val period = (frameRate as? RiveFrameRate.Capped)?.period
    private var nextRenderTimeNs = 0L

    /**
     * Returns how long to delay before requesting another platform frame callback.
     *
     * @param nowNs Current monotonic time in nanoseconds, using the same time base as frame
     *    callbacks.
     * @return Delay duration, or [Duration.ZERO] when no delay should be applied.
     */
    fun delayBeforeNextFrame(nowNs: Long): Duration {
        val activePeriod = period ?: return Duration.ZERO
        if (nextRenderTimeNs == 0L) {
            return Duration.ZERO
        }

        return (nextRenderTimeNs - nowNs).nanoseconds
            // Wake before the target time so the caller can await the next platform frame callback.
            .minus(earlyWake.coerceAtLeast(Duration.ZERO))
            .coerceAtLeast(Duration.ZERO)
            .coerceAtMost(activePeriod)
    }

    /**
     * Attempts to accept [frameTimeNs] as the next Rive frame.
     *
     * When accepted, the next capped frame target is advanced.
     *
     * @param frameTimeNs Platform frame time in nanoseconds.
     * @return True when the caller should advance and draw.
     */
    fun tryScheduleFrame(frameTimeNs: Long): Boolean {
        val activePeriodNs = period?.inWholeNanoseconds ?: return true
        if (nextRenderTimeNs != 0L && frameTimeNs < nextRenderTimeNs) {
            return false
        }

        if (nextRenderTimeNs == 0L) {
            nextRenderTimeNs = frameTimeNs + activePeriodNs
            return true
        }

        val periodsMissed = ((frameTimeNs - nextRenderTimeNs) / activePeriodNs) + 1L
        nextRenderTimeNs += periodsMissed * activePeriodNs

        return true
    }

    /**
     * Restarts pacing so the next frame callback renders immediately.
     *
     * Use this when a settled state machine becomes unsettled so idle time does not push the next
     * Rive frame farther into the future.
     */
    fun reset() {
        nextRenderTimeNs = 0L
    }
}
