package app.rive.runtime.kotlin


class Animation(val nativePointer: Long) {

    private external fun nativeName(nativePointer: Long): String
    private external fun nativeDuration(nativePointer: Long): Int
    private external fun nativeFps(nativePointer: Long): Int
    private external fun nativeWorkStart(nativePointer: Long): Int
    private external fun nativeWorkEnd(nativePointer: Long): Int
    private external fun nativeGetLoop(nativePointer: Long): Int
    private external fun nativeSetLoop(nativePointer: Long, value: Int)

    val duration: Int
        get() = nativeDuration(nativePointer)
    val fps: Int
        get() = nativeFps(nativePointer)
    val workStart: Int
        get() = nativeWorkStart(nativePointer)
    val workEnd: Int
        get() = nativeWorkEnd(nativePointer)
    val name: String
        get() = nativeName(nativePointer)
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
