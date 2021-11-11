package app.rive.runtime.kotlin.core

/**
 * [Animation]s as designed in the Rive animation editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [cppPointer] keeps track of this relationship.
 *
 * These can be used with [LinearAnimationInstance]s and [Artboard]s to draw frames
 *
 * The constructor uses a [cppPointer] to point to its c++ counterpart object.
 */
class Animation(val cppPointer: Long) : Playable() {

    private external fun cppName(cppPointer: Long): String
    private external fun cppDuration(cppPointer: Long): Int
    private external fun cppFps(cppPointer: Long): Int
    private external fun cppWorkStart(cppPointer: Long): Int
    private external fun cppWorkEnd(cppPointer: Long): Int
    private external fun cppGetLoop(cppPointer: Long): Int

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
    val loop: Loop
        get() {
            val intLoop = cppGetLoop(cppPointer)
            val loop = Loop.fromInt(intLoop) ?: throw IndexOutOfBoundsException()
            return loop
        }

    override fun toString(): String {
        return "Animation $name\n- Duration$duration\n- fps $fps\n- workStart $workStart\n- workEnd $workEnd"
    }
}
