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
class LinearAnimationInstance(val animation: Animation) {
    private var cppPointer: Long = constructor(animation.cppPointer)
    private external fun constructor(animationPointer: Long): Long
    private external fun cppAdvance(pointer: Long, elapsedTime: Float): Loop?
    private external fun cppApply(pointer: Long, artboardPointer: Long, mix: Float)
    private external fun cppGetTime(pointer: Long): Float
    private external fun cppSetTime(pointer: Long, time: Float)
    private external fun cppGetDirection(pointer: Long): Int
    private external fun cppSetDirection(pointer: Long, int: Int)


    /**
     * Advance the animation by the [elapsedTime] in seconds.
     *
     * Returns true if the animation will continue to animate after this advance.
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
    fun apply(artboard: Artboard, mix: Float = 1.0f) {
        cppApply(cppPointer, artboard.cppPointer, mix)
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
            val direction = Direction.fromInt(intDirection) ?: throw IndexOutOfBoundsException()
            return direction
        }
        set(direction) = cppSetDirection(cppPointer, direction.value)
}


