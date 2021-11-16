package app.rive.runtime.kotlin.renderers

abstract class BaseRenderer {
    internal abstract var cppPointer: Long

    protected abstract fun cleanupJNI(cppPointer: Long)

    /**
     * Remove the [Renderer] object from memory.
     */
    protected fun finalize() {
        cleanupJNI(cppPointer)
        cppPointer = 0
    }
}
