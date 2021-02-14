package app.rive.runtime.kotlin

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.Log

enum class Fit {
    FILL, CONTAIN, COVER, FIT_WIDTH, FIT_HEIGHT, NONE, SCALE_DOWN
}

enum class Alignment {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
}

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

    fun setMatrix(matrix: Matrix) {
        canvas.concat(matrix)
    }

    fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB) {
        nativeAlign(
            nativePointer,
            fit,
            alignment,
            targetBounds.nativePointer,
            sourceBounds.nativePointer
        )
    }

    fun cleanup() {
        cleanupJNI(nativePointer)
        nativePointer = 0
    }

    fun save(): Int {
        return canvas.save()
    }

    fun restore() {
        return canvas.restore()
    }

    fun translate(dx: Float, dy: Float) {
        return canvas.translate(dx, dy)
    }

    fun drawPath(path: Path, paint: Paint) {
        return canvas.drawPath(path, paint)
    }

    fun clipPath(path: Path): Boolean {
        return canvas.clipPath(path)
    }
}
