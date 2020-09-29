package app.rive.runtime.kotlin


class Animation {
    var nativePointer: Long

    constructor(_nativePointer: Long) : super() {
        nativePointer = _nativePointer
    }

    external private fun nativeName(nativePointer: Long): String

    companion object {
        init {
            System.loadLibrary("jnirivebridge")
        }
    }

    fun name(): String {
        return nativeName(nativePointer)
    }
}
