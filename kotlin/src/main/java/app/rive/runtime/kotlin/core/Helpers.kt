package app.rive.runtime.kotlin.core

import android.graphics.PointF

object Helpers {
    private external fun cppConvertToArtboardSpace(
        touchSpaceBoundsPointer: Long,
        touchSpaceLocation: PointF,
        fit: Fit,
        alignment: Alignment,
        artboardSpaceBoundsPointer: Long,
    ): PointF

    fun convertToArtboardSpace(
        touchBounds: AABB,
        touchLocation: PointF,
        fit: Fit,
        alignment: Alignment,
        artboardBounds: AABB,
    ): PointF {
        return cppConvertToArtboardSpace(
            touchBounds.cppPointer,
            touchLocation,
            fit,
            alignment,
            artboardBounds.cppPointer
        )
    }
}