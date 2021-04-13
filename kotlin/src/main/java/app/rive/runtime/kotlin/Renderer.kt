package app.rive.runtime.kotlin

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path

enum class Fit {
    FILL, CONTAIN, COVER, FIT_WIDTH, FIT_HEIGHT, NONE, SCALE_DOWN
}

enum class Alignment {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
}

/**
 * A [Renderer] is used to help draw an [Artboard] to a [Canvas]
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [nativePointer] keeps track of this relationship.
 *
 * Most of the functions implemented here are called from the c++ layer when artboards are
 * rendered.
 */
class Renderer(antialias: Boolean = true) {
    var nativePointer: Long
    lateinit var canvas: Canvas

    init {
        nativePointer = constructor(antialias)
    }

    private external fun nativeAlign(
        nativePointer: Long,
        fit: Fit,
        alignment: Alignment,
        targetBoundsPointer: Long,
        srcBoundsPointer: Long
    )

    private external fun constructor(antialias: Boolean): Long
    private external fun cleanupJNI(nativePointer: Long)

    /**
     * Passthrough to apply [matrix] to the [canvas]
     *
     * This function is used by the c++ layer.
     */
    fun setMatrix(matrix: Matrix) {
        canvas.concat(matrix)
    }

    /**
     * Instruct the native renderer how to align the artboard in the available space [targetBounds].
     *
     * Use [fit] and [alignment] to instruct how the [sourceBounds] should be matched into [targetBounds].
     *
     * typically it is expected to use an [Artboard]s bounds as [sourceBounds].
     */
    fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB) {
        nativeAlign(
            nativePointer,
            fit,
            alignment,
            targetBounds.nativePointer,
            sourceBounds.nativePointer
        )
    }

    /**
     * Remove the [Renderer] object from memory.
     */
    fun cleanup() {
        cleanupJNI(nativePointer)
        nativePointer = 0
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
