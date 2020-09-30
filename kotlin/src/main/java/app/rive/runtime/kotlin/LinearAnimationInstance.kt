package app.rive.runtime.kotlin

class LinearAnimationInstance {
    private var nativePointer: Long
    private var animation: Animation

    external private fun constructor(animationPointer: Long): Long
    external private fun nativeAdvance(pointer: Long, elapsedTime: Float)
    external private fun nativeApply(pointer: Long, artboardPointer: Long, mix: Float)
    external private fun nativeGetTime(pointer: Long): Float
    external private fun nativeSetTime(pointer: Long, time: Float)
    external private fun nativeAddObserver(pointer: Long, observerAddress: Long)

    constructor(_animation: Animation) : super() {
        animation = _animation
        nativePointer = constructor(animation.nativePointer);
    }

    companion object {
        init {
            System.loadLibrary("jnirivebridge")
        }
    }

    fun addObserver(observer: AnimationObserver) {
        nativeAddObserver(nativePointer, observer.address)
    }

    fun advance(elapsedTime: Float) {
        nativeAdvance(nativePointer, elapsedTime)
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
