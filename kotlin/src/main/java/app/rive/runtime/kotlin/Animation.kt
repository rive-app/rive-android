package app.rive.runtime.kotlin


class Animation {
    val nativePointer: Long

    external private fun nativeName(nativePointer: Long): String
    external private fun nativeDuration(nativePointer: Long): Int
    external private fun nativeFps(nativePointer: Long): Int
    external private fun nativeWorkStart(nativePointer: Long): Int
    external private fun nativeWorkEnd(nativePointer: Long): Int

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

    constructor(_nativePointer: Long) : super() {
        nativePointer = _nativePointer
    }

    companion object {
        init {
            System.loadLibrary("jnirivebridge")
        }
    }

    override fun toString(): String {
        return "Animation $name\n- Duration$duration\n- fps $fps\n- workStart $workStart\n- workEnd $workEnd"
    }
}
