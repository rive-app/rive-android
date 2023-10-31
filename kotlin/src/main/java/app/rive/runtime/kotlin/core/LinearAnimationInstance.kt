package app.rive.runtime.kotlin.core

import java.util.concurrent.locks.ReentrantLock

/**
 * The [LinearAnimationInstance] is a helper to wrap common operations to play an [animation].
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [unsafeCppPointer] keeps track of this relationship.
 *
 * Use this to keep track of an animation current state and progress. And to help [apply] changes
 * that the [animation] makes to components in an [Artboard].
 */
class LinearAnimationInstance(
    unsafeCppPointer: Long,
    private val artboardLock: ReentrantLock,
    var mix: Float = 1.0f
) :
    PlayableInstance, NativeObject(unsafeCppPointer) {

    private external fun cppAdvance(pointer: Long, elapsedTime: Float): Loop?
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
     * Advance the animation by the [elapsedTime] in seconds.
     *
     * Returns the [Loop] type associated with the current animation if this advance looped,
     *      otherwise it returns null.
     */
    fun advance(elapsedTime: Float): Loop? {
        synchronized(artboardLock) { return cppAdvance(cppPointer, elapsedTime) }
    }


    /**
     * Applies the animation instance's current set of transformations to an [artboard].
     *
     * The [mix] (a value between 0 and 1) is the strength at which the animation is mixed with
     * other animations applied to the [artboard].
     */
    fun apply() {
        synchronized(artboardLock) { cppApply(cppPointer, mix) }
    }

    /**
     * Applies and advances the animation instance in a single step.
     *
     * Returns true if the animation will continue to animate after this advance.
     */
    fun apply(elapsed: Float): Boolean {
        synchronized(artboardLock) { cppApply(cppPointer, mix) }
        val loopType = cppAdvance(cppPointer, elapsed)
        return loopType != Loop.ONESHOT
    }

    /**
     * Returns the current point in time at which this instance has advance
     * to.
     */
    val time: Float
        get() {
            return cppGetTime(cppPointer)
        }

    /**
     * Sets the animation's point in time to [time]
     */
    fun time(time: Float) {
        synchronized(artboardLock) { cppSetTime(cppPointer, time) }
    }

    /**
     * Configure the [Direction] of the animation instance
     * [Direction.FORWARDS] or [Direction.BACKWARDS]
     */
    var direction: Direction
        get() {
            val intDirection = cppGetDirection(cppPointer)
            return Direction.fromInt(intDirection) ?: throw IndexOutOfBoundsException()
        }
        set(direction) = synchronized(artboardLock) { cppSetDirection(cppPointer, direction.value) }

    /**
     * Get the duration of an animation in frames, this does not take [workStart]
     * and [workEnd] into account
     */
    val duration: Int
        get() = cppDuration(cppPointer)

    /**
     * Get the duration of an animation in frames, taking [workStart]
     * and [workEnd] into account
     */
    val effectiveDuration: Int
        get() {
            if (workStart == -1) {
                return duration
            }
            return workEnd - workStart
        }

    /**
     * Get the duration of an animation in seconds, taking [workStart]
     * and [workEnd] into account
     */
    val effectiveDurationInSeconds: Float
        get() = effectiveDuration.toFloat() / fps


    /**
     * Return the fps configured for the animation
     */
    val fps: Int
        get() = cppFps(cppPointer)

    /**
     * Return the offset in frames to the beginning of an animations work area.
     * Animations will start playing from here.
     */
    val workStart: Int
        get() = cppWorkStart(cppPointer)

    /**
     * Return the offset in frames to the end of an animations work area.
     * Animations will will loop, pingpong and stop once this is reached.
     */
    val workEnd: Int
        get() = cppWorkEnd(cppPointer)

    /**
     * Return the name given to an animation
     */
    override val name: String
        get() = cppName(cppPointer)

    /**
     * Return the offset in frames to the beginning of an animations.
     * Animations will start playing from here.
     */
    val startTime: Float
        get() {
            return if (workStart == -1) {
                0f
            } else {
                workStart.toFloat() / fps
            }
        }

    /**
     * Return the offset in frames to the end of an animation.
     */
    val endTime: Float
        get() {
            return if (workEnd == -1) {
                duration.toFloat() / fps
            } else {
                workEnd.toFloat() / fps
            }
        }

    /**
     * Configure the [Loop] mode configured against an animation. can be either
     * [Loop.LOOP], [Loop.ONESHOT], [Loop.PINGPONG] or [Loop.AUTO]
     */
    var loop: Loop
        get() {
            val intLoop = cppGetLoop(cppPointer)
            return Loop.fromIndex(intLoop)
        }
        set(loop) = synchronized(artboardLock) { cppSetLoop(cppPointer, loop.ordinal) }
}


