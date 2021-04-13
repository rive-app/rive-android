package app.rive.runtime.kotlin

object Rive {
    private external fun nativeInitialize()

    /**
     * Initialises Rive.
     *
     * This loads the c++ libraries required to use Rive objects and then
     * updates the c++ environment with a pointer to the JavaVM so that
     * it can interact with Java objects. 
     */
    fun init() {
        System.loadLibrary("jnirivebridge")
        nativeInitialize()
    }


}