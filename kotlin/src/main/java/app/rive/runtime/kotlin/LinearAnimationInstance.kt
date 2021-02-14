package app.rive.runtime.kotlin

class LinearAnimationInstance(val animation: Animation) {
    private var nativePointer: Long = constructor(animation.nativePointer)
    var mix: Float = 1.0f

    private external fun constructor(animationPointer: Long): Long
    private external fun nativeAdvance(pointer: Long, elapsedTime: Float): Loop?
    private external fun nativeApply(pointer: Long, artboardPointer: Long, mix: Float)
    private external fun nativeGetTime(pointer: Long): Float
    private external fun nativeSetTime(pointer: Long, time: Float)

    fun advance(elapsedTime: Float): Loop? {
        return nativeAdvance(nativePointer, elapsedTime)
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