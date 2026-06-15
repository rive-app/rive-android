#pragma once

#include <EGL/egl.h>
#include <cassert>
#include <string>

namespace rive_android
{

/**
 * Reads and clears the current thread's EGL error, with a defensive fallback
 * when `eglGetError()` unexpectedly reports `EGL_SUCCESS` on a known failure
 * path.
 *
 * @param fallback EGL error to use when `eglGetError()` returns success.
 * @param logTag Log tag used for warning output when fallback is used.
 */
EGLint consume_egl_error_or_default(EGLint fallback, const char* logTag);

/**
 * EGL-specific operation result used by the Legacy Android runtime, where the
 * worker and thread-state layer are directly coupled to EGL.
 *
 * The Compose/new Android runtime uses the backend-agnostic
 * `RenderContextResult` in
 * [models/render_context_result.hpp](/Users/erikuggeldahl/rive/packages/runtime_android/kotlin/src/main/cpp/include/models/render_context_result.hpp)
 * instead.
 */
struct EGLResult
{
    /// Identifies which EGL operation failed when building an EGLResult.
    enum class FailureOperation
    {
        none,
        makeCurrent,
        swapBuffers,
        recover,
    };

    FailureOperation operation = FailureOperation::none;
    EGLint error = EGL_SUCCESS;

    /// Creates a successful result with no EGL error.
    static EGLResult Ok() { return EGLResult{}; }

    /// Creates a successful recovery result for render-context lifecycle
    /// events.
    static EGLResult Recovered()
    {
        EGLResult result;
        result.operation = FailureOperation::recover;
        return result;
    }

    /// Creates a failed result for an operation using the supplied EGL error.
    static EGLResult Failure(FailureOperation failureOp, EGLint errorCode)
    {
        assert(failureOp != FailureOperation::none);
        EGLResult result;
        result.operation = failureOp;
        result.error = errorCode;
        return result;
    }

    /// Returns true when this result contains EGL_SUCCESS.
    [[nodiscard]] bool isSuccess() const { return error == EGL_SUCCESS; }

    /// Returns true when the EGL error should be treated as context-fatal.
    [[nodiscard]] bool isFatal() const
    {
        return !isSuccess() && IsFatalEGLError(error);
    }

    /// Returns a compact, human-readable summary of this result.
    [[nodiscard]] std::string summary() const;

    /// Returns a stable string label for logging a failure operation.
    static const char* FailureOperationName(FailureOperation operation);

private:
    /// Classifies EGL errors into fatal vs transient for recovery decisions.
    static bool IsFatalEGLError(EGLint error);
};

} // namespace rive_android
