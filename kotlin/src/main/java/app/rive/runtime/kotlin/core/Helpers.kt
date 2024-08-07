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

    /**
     * Retrieves the current stack trace of the thread and optionally trims elements related to stack trace retrieval.
     *
     * @param trim If true, trims the stack trace to remove methods related to stack trace retrieval itself; otherwise, returns the full stack trace.
     * @return A sequence of [StackTraceElement] representing the current stack trace of the thread.
     */
    fun getCurrentStackTrace(trim: Boolean = true): Sequence<StackTraceElement> {
        val stackTrace = Thread.currentThread().stackTrace.asSequence()

        return if (trim) {
            stackTrace
                // Drop all Stack elements until we find this Helper class
                .dropWhile { it.className != javaClass.name }
                // Then remove all stack elements of this Helper class
                .dropWhile { it.className == javaClass.name }
        } else {
            stackTrace
        }
    }
}