package app.rive.runtime.kotlin

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.times

// NOTE: included for alternative setMatrix implementation
//import kotlin.math.atan2
//import kotlin.math.cos
//import kotlin.math.sqrt


enum class Fit {
    FILL, CONTAIN, COVER, FIT_WIDTH, FIT_HEIGHT, NONE, SCALE_DOWN
}

enum class Alignment {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
}

class Renderer {
    var nativePointer: Long
    lateinit var canvas: Canvas

    external private fun nativeAlign(
        nativePointer: Long,
        fit: Fit,
        alignment: Alignment,
        targetBoundsPointer: Long,
        srcBoundsPointer: Long
    )

    external private fun constructor(): Long
    external private fun cleanupJNI(nativePointer: Long)

    companion object {
        init {
            System.loadLibrary("jnirivebridge")
        }
    }

    constructor() {
        nativePointer = constructor();
    }

    fun setMatrix(matrix: Matrix) {
        // TODO: use translate, scale, rotate instead?

        canvas.setMatrix(
            canvas.getMatrix().times(matrix)
        )
//        NOTE: alternative implementation using translate/scale/rotate
//        val matrixVals = FloatArray(9)
//        matrix.getValues(matrixVals)
//
//        var rotation = (
//                atan2(
//                    matrixVals[Matrix.MSKEW_Y],
//                    matrixVals[Matrix.MSCALE_Y]
//                ) * 180 / Math.PI
//                ).toFloat()
//
//        var scaleX = sqrt(
//            matrixVals[Matrix.MSCALE_X] * matrixVals[Matrix.MSCALE_X] +
//                    matrixVals[Matrix.MSKEW_X] * matrixVals[Matrix.MSKEW_X]
//        )
//        var scaleY =
//            sqrt(
//                matrixVals[Matrix.MSCALE_Y] * matrixVals[Matrix.MSCALE_Y] +
//                        matrixVals[Matrix.MSKEW_Y] * matrixVals[Matrix.MSKEW_Y]
//            )
//
//        canvas.translate(matrixVals[Matrix.MTRANS_X], matrixVals[Matrix.MTRANS_Y]);
//        canvas.scale(scaleX, scaleY);
//        canvas.rotate(rotation);

    }

    fun align(fit: Fit, alignment: Alignment, targetBounds: AABB, sourceBounds: AABB) {
        nativeAlign(
            nativePointer,
            fit,
            alignment,
            targetBounds.nativePointer,
            sourceBounds.nativePointer
        );
    }

    fun cleanup() {
        cleanupJNI(nativePointer);
        nativePointer = 0;
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
