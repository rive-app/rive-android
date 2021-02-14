package app.rive.runtime.kotlin

object Rive {

    fun init() {
        System.loadLibrary("jnirivebridge")
    }
}