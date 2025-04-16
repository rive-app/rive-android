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
        scaleFactor: Float
    ): PointF

    fun convertToArtboardSpace(
        touchBounds: RectF,
        touchLocation: PointF,
        fit: Fit,
        alignment: Alignment,
        artboardBounds: RectF,
        scaleFactor: Float = 1.0f,
    ): PointF {
        return cppConvertToArtboardSpace(
            touchBounds,
            touchLocation,
            fit,
            alignment,
            artboardBounds,
            scaleFactor
        )
    }
}
