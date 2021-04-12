package app.rive.runtime.kotlin

object Rive {

    fun init() {
        //        TODO: initialize javavm here?
        System.loadLibrary("jnirivebridge")
    }
}