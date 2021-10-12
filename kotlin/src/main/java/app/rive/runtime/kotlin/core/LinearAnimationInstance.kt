package app.rive.runtime.kotlin.core

/**
 * The [LinearAnimationInstance] is a helper to wrap common operations to play an [animation].
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [cppPointer] keeps track of this relationship.
 *
 * Use this to keep track of an animation current state and progress. And to help [apply] changes
 * that the [animation] makes to components in an [Artboard].
 */
class LinearAnimationInstance(val animation: Animation, var mix: Float = 1.0f) :
    PlayableInstance() {
    private var cppPointer: Long = constructor(animation.cppPointer)
    private external fun constructor(animationPointer: Long): Long
    private external fun cppAdvance(pointer: Long, elapsedTime: Float): Loop?
    private external fun cppApply(pointer: Long, artboardPointer: Long, mix: Float)
    private external fun cppGetTime(pointer: Long): Float
    private external fun cppSetTime(pointer: Long, time: Float)
    private external fun cppGetDirection(pointer: Long): Int
    private external fun cppSetDirection(pointer: Long, int: Int)
    private external fun cppGetLoop(cppPointer: Long): Int
    private external fun cppSetLoop(cppPointer: Long, value: Int)


    /**
     * Advance the animation by the [elapsedTime] in seconds.
     *
     * Returns the [Loop] type associated with the current animation if this advance looped,
     *      otherwise it returns null.
     */
    fun advance(elapsedTime: Float): Loop? {
        return cppAdvance(cppPointer, elapsedTime)
    }


    /**
     * Applies the animation instance's current set of transformations to an [artboard].
     *
     * The [mix] (a value between 0 and 1) is the strength at which the animation is mixed with
     * other animations applied to the [artboard].
     */
    fun apply(artboard: Artboard) {
        cppApply(cppPointer, artboard.cppPointer, mix)
    }

    /**
     * Applies and advances the animation instance in a single step.
     *
     * Returns true if the animation will continue to animate after this advance.
     */
    override fun apply(artboard: Artboard, elapsed: Float): Boolean {
        cppApply(cppPointer, artboard.cppPointer, mix)
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
        cppSetTime(cppPointer, time)
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
        set(direction) = cppSetDirection(cppPointer, direction.value)

    /**
     * Configure the [Loop] mode configured against an animation. can be either
     * [Loop.LOOP], [Loop.ONESHOT], [Loop.PINGPONG] or [Loop.AUTO]
     */
    var loop: Loop
        get() {
            val intLoop = cppGetLoop(cppPointer)
            val loop = Loop.fromInt(intLoop) ?: throw IndexOutOfBoundsException()
            return loop
        }
        set(loop) = cppSetLoop(cppPointer, loop.value)
}


