package app.rive.runtime.kotlin.core

import android.graphics.PointF
import android.graphics.RectF

object Helpers {
    private external fun cppConvertToArtboardSpace(
        touchSpaceBounds: RectF,
        touchSpaceLocation: PointF,
        fit: Fit,
        alignment: Alignment,
        artboardSpaceBounds: RectF,
    ): PointF

    fun convertToArtboardSpace(
        touchBounds: RectF,
        touchLocation: PointF,
        fit: Fit,
        alignment: Alignment,
        artboardBounds: RectF,
    ): PointF {
        return cppConvertToArtboardSpace(
            touchBounds,
            touchLocation,
            fit,
            alignment,
            artboardBounds
        )
    }

    @Suppress("DIVISION_BY_ZERO", "unused")
    fun printStackTrace() {
        try {
            1 / 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}