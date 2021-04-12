package app.rive.runtime.kotlin

class LinearAnimationInstance(val animation: Animation) {
    private var nativePointer: Long = constructor(animation.nativePointer)
    var mix: Float = 1.0f

    private external fun constructor(animationPointer: Long): Long
    private external fun nativeAdvance(pointer: Long, elapsedTime: Float): Loop?
    private external fun nativeApply(pointer: Long, artboardPointer: Long, mix: Float)
    private external fun nativeGetTime(pointer: Long): Float
    private external fun nativeSetTime(pointer: Long, time: Float)


    /**
     * Advance the animation by the [elapsedTime]. Returns true if the
     * animation will continue to animate after this advance.
     */
    fun advance(elapsedTime: Float): Loop? {
        return nativeAdvance(nativePointer, elapsedTime)
    }


    /**
     * Applies the animation instance's current set of transformations to an [artboard].
     * The mix (a value between 0 and 1) is the strength at which the animation is mixed with
     * other animations applied to the artboard.
     */
    fun apply(artboard: Artboard, mix: Float = 1.0f) {
        nativeApply(nativePointer, artboard.nativePointer, mix)
    }

    /**
     * Returns the current point in time at which this instance has advance
     * to
     */
    fun time(): Float {
        return nativeGetTime(nativePointer)
    }

    /**
     * Sets the animation's point in time to [time]
     */
    fun time(time: Float) {
        nativeSetTime(nativePointer, time)
    }
}

enum class Loop(val value: Int) {
    ONESHOT(0),
    LOOP(1),
    PINGPONG(2),
    NONE(3);

    companion object {
        private val map = values().associateBy(Loop::value)
        fun fromInt(type: Int) = map[type]
    }
}