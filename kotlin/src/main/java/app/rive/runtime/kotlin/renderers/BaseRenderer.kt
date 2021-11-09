package app.rive.runtime.kotlin.renderers

import app.rive.runtime.kotlin.core.AABB
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.Fit


abstract class BaseRenderer {
    internal abstract var cppPointer: Long

    abstract fun draw(artboard: Artboard)
    protected abstract fun cppDraw(artboardPointer: Long, rendererPointer: Long)
    protected abstract fun cleanupJNI(cppPointer: Long)
    abstract fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB)

    /**
     * Remove the [Renderer] object from memory.
     */
    fun cleanup() {
        cleanupJNI(cppPointer)
        cppPointer = 0
    }
}
