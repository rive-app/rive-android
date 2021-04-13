package app.rive.runtime.kotlin.core

/**
 * [Animation]s as designed in the Rive animation editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [nativePointer] keeps track of this relationship.
 *
 * These can be used with [LinearAnimationInstance]s and [Artboard]s to draw frames
 *
 * The constructor uses a [nativePointer] to point to its c++ counterpart object.
 */
class Animation(val nativePointer: Long) {

    private external fun nativeName(nativePointer: Long): String
    private external fun nativeDuration(nativePointer: Long): Int
    private external fun nativeFps(nativePointer: Long): Int
    private external fun nativeWorkStart(nativePointer: Long): Int
    private external fun nativeWorkEnd(nativePointer: Long): Int
    private external fun nativeGetLoop(nativePointer: Long): Int
    private external fun nativeSetLoop(nativePointer: Long, value: Int)

    /**
     * Get the duration of an animation in frames, this does not take [workStart]
     * and [workEnd] into account
     */
    val duration: Int
        get() = nativeDuration(nativePointer)

    /**
     * Get the duration of an animation in frames, taking [workStart]
     * and [workEnd] into account
     */
    val effectiveDuration: Int
        get() {
            if (workStart == -1) {
                return duration
            }
            return workEnd-workStart
        }


    /**
     * Return the fps configured for the animation
     */
    val fps: Int
        get() = nativeFps(nativePointer)

    /**
     * Return the offset in frames to the beginning of an animations work area.
     * Animations will start playing from here.
     */
    val workStart: Int
        get() = nativeWorkStart(nativePointer)

    /**
     * Return the offset in frames to the end of an animations work area.
     * Animations will will loop, pingpong and stop once this is reached.
     */
    val workEnd: Int
        get() = nativeWorkEnd(nativePointer)

    /**
     * Return the name given to an animation
     */
    val name: String
        get() = nativeName(nativePointer)

    /**
     * Configure the [Loop] mode configured against an animation. can be either
     * [Loop.LOOP], [Loop.ONESHOT], [Loop.PINGPONG] or [Loop.NONE]
     */
    var loop: Loop
        get() {
            val intLoop = nativeGetLoop(nativePointer)
            val loop = Loop.fromInt(intLoop) ?: throw IndexOutOfBoundsException()
            return loop
        }
        set(loop) = nativeSetLoop(nativePointer, loop.value)

    override fun toString(): String {
        return "Animation $name\n- Duration$duration\n- fps $fps\n- workStart $workStart\n- workEnd $workEnd"
    }
}
