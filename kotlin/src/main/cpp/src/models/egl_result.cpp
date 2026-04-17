#include "models/egl_result.hpp"

#include "helpers/egl_error.hpp"
#include "helpers/rive_log.hpp"

namespace rive_android
{

EGLint consume_egl_error_or_default(EGLint fallback, const char* logTag)
{
    const EGLint error = eglGetError();
    if (error == EGL_SUCCESS)
    {
        RiveLogW(
            logTag,
            "eglGetError() returned EGL_SUCCESS unexpectedly; falling back to %s.",
            EGLErrorString(fallback).c_str());
        return fallback;
    }
    return error;
}

const char* EGLResult::FailureOperationName(
    EGLResult::FailureOperation operation)
{
    switch (operation)
    {
        case FailureOperation::none:
            return "none";
        case FailureOperation::makeCurrent:
            return "makeCurrent";
        case FailureOperation::swapBuffers:
            return "swapBuffers";
        case FailureOperation::recover:
            return "recover";
    }
    return "unknown";
}

bool EGLResult::IsFatalEGLError(EGLint error)
{
    switch (error)
    {
        case EGL_CONTEXT_LOST:
        case EGL_BAD_CONTEXT:
        case EGL_BAD_DISPLAY:
        case EGL_NOT_INITIALIZED:
            return true;
        case EGL_BAD_SURFACE:
        case EGL_BAD_NATIVE_WINDOW:
        case EGL_BAD_CURRENT_SURFACE:
        case EGL_BAD_MATCH:
            return false;
        default:
            // Unknown errors are treated as fatal defensively.
            return true;
    }
}

std::string EGLResult::summary() const
{
    const std::string errorString = EGLErrorString(error);
    return std::string("operation: ") + FailureOperationName(operation) +
           " error: " + errorString +
           " fatal: " + (isFatal() ? "true" : "false");
}

} // namespace rive_android
