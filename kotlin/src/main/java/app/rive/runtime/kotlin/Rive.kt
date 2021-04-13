package app.rive.runtime.kotlin

object Rive {
    private external fun nativeInitialize()

    fun init() {
        System.loadLibrary("jnirivebridge")
        nativeInitialize()
    }


}