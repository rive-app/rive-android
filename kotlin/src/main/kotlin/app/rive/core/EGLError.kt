package app.rive.core

import android.opengl.EGL14

/** Utility to convert EGL error codes to human-readable strings. */
object EGLError {
    val EGL_ERROR_MAP = mapOf(
        Pair(EGL14.EGL_SUCCESS, "EGL_SUCCESS"),
        Pair(EGL14.EGL_NOT_INITIALIZED, "EGL_NOT_INITIALIZED"),
        Pair(EGL14.EGL_BAD_ACCESS, "EGL_BAD_ACCESS"),
        Pair(EGL14.EGL_BAD_ALLOC, "EGL_BAD_ALLOC"),
        Pair(EGL14.EGL_BAD_ATTRIBUTE, "EGL_BAD_ATTRIBUTE"),
        Pair(EGL14.EGL_BAD_CONTEXT, "EGL_BAD_CONTEXT"),
        Pair(EGL14.EGL_BAD_CONFIG, "EGL_BAD_CONFIG"),
        Pair(EGL14.EGL_BAD_CURRENT_SURFACE, "EGL_BAD_CURRENT_SURFACE"),
        Pair(EGL14.EGL_BAD_DISPLAY, "EGL_BAD_DISPLAY"),
        Pair(EGL14.EGL_BAD_SURFACE, "EGL_BAD_SURFACE"),
        Pair(EGL14.EGL_BAD_MATCH, "EGL_BAD_MATCH"),
        Pair(EGL14.EGL_BAD_PARAMETER, "EGL_BAD_PARAMETER"),
        Pair(EGL14.EGL_BAD_NATIVE_PIXMAP, "EGL_BAD_NATIVE_PIXMAP"),
        Pair(EGL14.EGL_BAD_NATIVE_WINDOW, "EGL_BAD_NATIVE_WINDOW"),
        Pair(EGL14.EGL_CONTEXT_LOST, "EGL_CONTEXT_LOST")
    )

    /**
     * Converts an EGL error code to a human-readable string.
     *
     * @param eglError The EGL error code.
     * @return A human-readable string representing the EGL error.
     */
    fun errorString(eglError: Int): String =
        EGL_ERROR_MAP[eglError] ?: "Unknown EGL error 0x${eglError.toString(16)}"
}
