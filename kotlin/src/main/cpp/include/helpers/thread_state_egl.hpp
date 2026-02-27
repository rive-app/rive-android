#pragma once

#include "helpers/general.hpp"
#include "helpers/tracer.hpp"

#include <android/native_window.h>
#include <cassert>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <jni.h>
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

/// Captures the outcome of an EGL operation and, if an error, whether the error
/// is fatal.
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
    bool isSuccess() const { return error == EGL_SUCCESS; }

    /// Returns true when the EGL error should be treated as context-fatal.
    bool isFatal() const { return !isSuccess() && IsFatalEGLError(error); }

    /// Returns a compact, human-readable summary of this result.
    std::string summary() const;

    /// Returns a stable string label for logging a failure operation.
    static const char* FailureOperationName(FailureOperation operation);

private:
    /// Classifies EGL errors into fatal vs transient for recovery decisions.
    static bool IsFatalEGLError(EGLint error);
};

class DrawableThreadState
{
public:
    virtual ~DrawableThreadState() = default;
    virtual EGLResult swapBuffers() = 0;
    virtual EGLResult recoverAfterContextLoss() { return EGLResult::Ok(); }
};

class EGLThreadState : public DrawableThreadState
{
public:
    EGLThreadState();

    ~EGLThreadState() override = 0;

    EGLSurface createEGLSurface(ANativeWindow*);
    virtual void destroySurface(EGLSurface) = 0;

    virtual EGLResult makeCurrent(EGLSurface) = 0;
    EGLResult swapBuffers() override;

    /**
     * Rebuilds EGL display/config/context state after a fatal EGL failure.
     *
     * Call this from the worker thread when rendering has detected
     * context-integrity loss (e.g. failed makeCurrent/swap with fatal error)
     * and normal frame execution should be replaced by recovery attempts.
     *
     * @return EGLResult::Ok() when EGL state was reinitialized successfully;
     * otherwise an EGLResult failure describing the recovery error.
     */
    EGLResult recoverAfterContextLoss() override;

protected:
    EGLResult initializeEGLState();
    void teardownEGLState();

    bool hasValidContext() const
    {
        return m_display != EGL_NO_DISPLAY && m_context != EGL_NO_CONTEXT;
    }

    EGLSurface m_currentSurface = EGL_NO_SURFACE;
    EGLDisplay m_display = EGL_NO_DISPLAY;
    EGLContext m_context = EGL_NO_CONTEXT;
    EGLConfig m_config = static_cast<EGLConfig>(nullptr);
};

class CanvasThreadState : public DrawableThreadState
{
public:
    EGLResult swapBuffers() override { return EGLResult::Ok(); }
};
} // namespace rive_android
