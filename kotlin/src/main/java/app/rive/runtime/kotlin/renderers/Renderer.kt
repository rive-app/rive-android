package app.rive.runtime.kotlin.renderers

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import app.rive.runtime.kotlin.core.AABB
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.Fit

/**
 * A [Renderer] is used to help draw an [Artboard] to a [Canvas]
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [cppPointer] keeps track of this relationship.
 *
 * Most of the functions implemented here are called from the c++ layer when artboards are
 * rendered.
 */
class Renderer(antialias: Boolean = true) : BaseRenderer() {
    private lateinit var canvas: Canvas

    override var cppPointer: Long = constructor(antialias)

    external override fun cleanupJNI(cppPointer: Long)
    external override fun cppDraw(artboardPointer: Long, rendererPointer: Long)

    private external fun constructor(antialias: Boolean): Long
    private external fun cppAlign(
        cppPointer: Long,
        fit: Fit,
        alignment: Alignment,
        targetBoundsPointer: Long,
        srcBoundsPointer: Long
    )

    fun draw(artboard: Artboard, canvas: Canvas) {
        this.canvas = canvas
        val saved = canvas.save()
        draw(artboard)
        canvas.restoreToCount(saved)
    }

    override fun draw(artboard: Artboard) {
        cppDraw(artboard.cppPointer, this.cppPointer)
    }

    /**
     * Passthrough to apply [matrix] to the [canvas]
     *
     * This function is used by the c++ layer.
     */
    fun setMatrix(matrix: Matrix) {
        canvas.concat(matrix)
    }

    /**
     * Instruct the cpp renderer how to align the artboard in the available space [targetBounds].
     *
     * Use [fit] and [alignment] to instruct how the [sourceBounds] should be matched into [targetBounds].
     *
     * typically it is expected to use an [Artboard]s bounds as [sourceBounds].
     */
    override fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB) {
        cppAlign(
            cppPointer,
            fit,
            alignment,
            targetBounds.cppPointer,
            sourceBounds.cppPointer
        )
    }


    /**
     * Passthrough to apply [save] to the [canvas]
     *
     * This function is used by the c++ layer.
     */
    fun save(): Int {
        return canvas.save()
    }

    /**
     * Passthrough to apply [restore] to the [canvas]
     *
     * This function is used by the c++ layer.
     */
    fun restore() {
        return canvas.restore()
    }

    /**
     * Passthrough to apply [translate] to the [canvas]
     *
     * This function is used by the c++ layer.
     */
    fun translate(dx: Float, dy: Float) {
        return canvas.translate(dx, dy)
    }

    /**
     * Passthrough to apply [drawPath] to the [canvas]
     *
     * This function is used by the c++ layer.
     */
    fun drawPath(path: Path, paint: Paint) {
        return canvas.drawPath(path, paint)
    }

    /**
     * Passthrough to apply [clipPath] to the [canvas]
     *
     * This function is used by the c++ layer.
     */
    fun clipPath(path: Path): Boolean {
        return canvas.clipPath(path)
    }
}
