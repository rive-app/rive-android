package app.rive.runtime.kotlin.renderers

import android.util.Log

abstract class BaseRenderer {
    internal abstract var cppPointer: Long

    protected abstract fun cleanupJNI(cppPointer: Long)
}
