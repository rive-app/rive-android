package app.rive.runtime.kotlin

enum class Loop(val value: Int) {
    ONESHOT(0),
    LOOP(1),
    PINGPONG(2);

    companion object {
        private val map = Loop.values().associateBy(Loop::value)
        fun fromInt(type: Int) = map[type]
    }
}

class LinearAnimationInstance {
    private var nativePointer: Long
    var animation: Animation
        private set
    var mix: Float = 1.0f

    external private fun constructor(animationPointer: Long): Long
    external private fun nativeAdvance(pointer: Long, elapsedTime: Float): Loop?
    external private fun nativeApply(pointer: Long, artboardPointer: Long, mix: Float)
    external private fun nativeGetTime(pointer: Long): Float
    external private fun nativeSetTime(pointer: Long, time: Float)
    external private fun nativeSetLoop(pointer: Long, loop: Int)

    constructor(_animation: Animation) : super() {
        animation = _animation
        nativePointer = constructor(animation.nativePointer);
    }

    companion object {
        init {
            System.loadLibrary("jnirivebridge")
        }
    }

    fun advance(elapsedTime: Float): Loop? {
        val loop = nativeAdvance(nativePointer, elapsedTime)
        return loop
    }

    fun apply(artboard: Artboard, mix: Float = 1.0f) {
        nativeApply(nativePointer, artboard.nativePointer, mix)
    }

    fun time(): Float {
        return nativeGetTime(nativePointer)
    }

    fun time(_time: Float) {
        nativeSetTime(nativePointer, _time)
    }
}
