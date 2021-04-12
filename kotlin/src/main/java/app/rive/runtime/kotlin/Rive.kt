package app.rive.runtime.kotlin

object Rive {
    private external fun nativeInitialize()

    fun init() {
        //        TODO: initialize javavm here?
        System.loadLibrary("jnirivebridge")
        nativeInitialize()
    }


}