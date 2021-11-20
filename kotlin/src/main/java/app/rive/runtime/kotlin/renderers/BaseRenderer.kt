package app.rive.runtime.kotlin.renderers

abstract class BaseRenderer {
    protected abstract var cppPointer: Long
    protected abstract fun cleanupJNI(cppPointer: Long)
}
