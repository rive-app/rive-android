package app.rive.runtime.kotlin.core

import java.util.concurrent.locks.ReentrantLock

/**
 * The [LinearAnimationInstance] is a helper to wrap common operations to play an animation.
 *
 * Use this to keep track of an animation's current state and progress. You may also [apply] changes
 * that the animation makes to components in an [Artboard].
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 */
class LinearAnimationInstance(
    unsafeCppPointer: Long,
    private val lock: ReentrantLock,
    var mix: Float = 1.0f,
) :
    PlayableInstance, NativeObject(unsafeCppPointer) {

    private external fun cppAdvance(pointer: Long, elapsedTime: Float): Loop?
    private external fun cppAdvanceAndGetResult(pointer: Long, elapsedTime: Float): AdvanceResult
    private external fun cppApply(pointer: Long, mix: Float)
    private external fun cppGetTime(pointer: Long): Float
    private external fun cppSetTime(pointer: Long, time: Float)
    private external fun cppGetDirection(pointer: Long): Int
    private external fun cppSetDirection(pointer: Long, int: Int)
    private external fun cppGetLoop(cppPointer: Long): Int
    private external fun cppSetLoop(cppPointer: Long, value: Int)
    private external fun cppName(cppPointer: Long): String
    private external fun cppDuration(cppPointer: Long): Int
    private external fun cppFps(cppPointer: Long): Int
    private external fun cppWorkStart(cppPointer: Long): Int
    private external fun cppWorkEnd(cppPointer: Long): Int

    external override fun cppDelete(pointer: Long)

    /**
     * Advance the animation.
     *
     * @param elapsedTime The time in seconds to advance by.
     * @return The [Loop] type associated with the current animation if this advance caused a loop,
     *    otherwise null.
     */
    @Deprecated(
        "Use advanceAndGetResult instead.",
        ReplaceWith("advanceAndGetResult(elapsedTime)")
    )
    fun advance(elapsedTime: Float): Loop? {
        synchronized(lock) { return cppAdvance(cppPointer, elapsedTime) }
    }

    /**
     * Advance the animation and return the result.
     *
     * @param elapsedTime The time in seconds to advance by.
     * @return An [AdvanceResult] enum value indicating the outcome of the advance step
     */
    fun advanceAndGetResult(elapsedTime: Float): AdvanceResult {
        synchronized(lock) { return cppAdvanceAndGetResult(cppPointer, elapsedTime) }
    }

    /**
     * Applies the animation instance's current set of transformations to an [Artboard].
     *
     * Uses the [mix] property (a value between 0 and 1) to set the strength at which the animation
     * is mixed with other animations applied to the [Artboard].
     */
    fun apply() {
        synchronized(lock) { cppApply(cppPointer, mix) }
    }

    /**
     * Applies and advances the animation instance in a single step.
     *
     * @return `true` if the animation will continue to animate after this advance.
     */
    fun apply(elapsed: Float): Boolean {
        synchronized(lock) { cppApply(cppPointer, mix) }
        val result = advanceAndGetResult(elapsed)
        return when (result) {
            AdvanceResult.ADVANCED, AdvanceResult.LOOP, AdvanceResult.PINGPONG -> true
            AdvanceResult.ONESHOT, AdvanceResult.NONE -> false
        }
    }

    /** The elapsed time that this instance has played to. */
    val time: Float
        get() {
            return cppGetTime(cppPointer)
        }

    /** Seeks the animation to [time]. */
    fun time(time: Float) {
        synchronized(lock) { cppSetTime(cppPointer, time) }
    }

    /**
     * Configure the animation to play [forwards][Direction.FORWARDS] or
     * [backwards][Direction.BACKWARDS].
     */
    var direction: Direction
        get() {
            val direction = Direction.fromInt(cppGetDirection(cppPointer))
            check(direction != null)
            return direction
        }
        set(direction) = synchronized(lock) { cppSetDirection(cppPointer, direction.value) }

    /**
     * The duration of an animation in frames. This does not take [workStart] and [workEnd] into
     * account.
     */
    val duration: Int
        get() = cppDuration(cppPointer)

    /** The duration of this animation in frames, taking [workStart] and [workEnd] into account. */
    val effectiveDuration: Int
        get() {
            if (workStart == -1) {
                return duration
            }
            return workEnd - workStart
        }

    /** The duration of an animation in seconds, taking [workStart] and [workEnd] into account. */
    val effectiveDurationInSeconds: Float
        get() = effectiveDuration.toFloat() / fps

    /** Return the frames per second (FPS) configured for this animation. */
    val fps: Int
        get() = cppFps(cppPointer)

    /**
     * The offset in frames to the beginning of an animations work area. Animations will start
     * playing from here.
     */
    val workStart: Int
        get() = cppWorkStart(cppPointer)

    /**
     * The offset in frames to the end of an animations work area. Animations will execute their
     * loop behavior once this is reached.
     */
    val workEnd: Int
        get() = cppWorkEnd(cppPointer)

    /** The name given to this animation. */
    override val name: String
        get() = cppName(cppPointer)

    /**
     * The offset in seconds to the beginning of the animation. Animations will start playing from
     * here.
     */
    val startTime: Float
        get() {
            return if (workStart == -1) {
                0f
            } else {
                workStart.toFloat() / fps
            }
        }

    /** The offset in seconds to the end of the animation. */
    val endTime: Float
        get() {
            return if (workEnd == -1) {
                duration.toFloat() / fps
            } else {
                workEnd.toFloat() / fps
            }
        }

    /**
     * Configure the [Loop] mode for this animation. Can be either [Loop.LOOP], [Loop.ONESHOT],
     * [Loop.PINGPONG] or [Loop.AUTO].
     */
    var loop: Loop
        get() {
            val intLoop = cppGetLoop(cppPointer)
            return Loop.fromIndex(intLoop)
        }
        set(loop) = synchronized(lock) { cppSetLoop(cppPointer, loop.ordinal) }
}
