#include "helpers/thread_state_pls.hpp"

#include "helpers/egl_error.hpp"
#include "helpers/rive_log.hpp"

namespace rive_android
{
constexpr auto* TAG = "RiveLN/PLSThreadState";

PLSThreadState::PLSThreadState()
{
    const EGLResult initResult = initializePLSResources();
    if (!initResult.isSuccess())
    {
        RiveLogE(TAG,
                 "Failed to initialize Rive Renderer resources: %s",
                 initResult.summary().c_str());
    }
}

PLSThreadState::~PLSThreadState()
{
    RiveLogD(TAG, "Deleting Rive Renderer thread state.");
    teardownPLSResources();
}

EGLResult PLSThreadState::initializePLSResources()
{
    if (!hasValidContext())
    {
        return EGLResult::Failure(EGLResult::FailureOperation::recover,
                                  EGL_BAD_CONTEXT);
    }

    // Create a 1x1 Pbuffer surface that we can use to guarantee m_context is
    // always current on this thread.
    const EGLint PbufferAttrs[] = {
        EGL_WIDTH,
        1,
        EGL_HEIGHT,
        1,
        EGL_NONE,
    };
    RiveLogD(TAG, "Creating background P-buffer surface.");
    m_backgroundSurface =
        eglCreatePbufferSurface(m_display, m_config, PbufferAttrs);
    if (m_backgroundSurface == EGL_NO_SURFACE)
    {
        RiveLogE(TAG,
                 "Failed to create a 1x1 background Pbuffer surface for PLS.");
        return EGLResult::Failure(EGLResult::FailureOperation::recover,
                                  EGL_BAD_SURFACE);
    }

    RiveLogD(TAG, "Making background surface current.");
    const EGLResult makeCurrentResult = makeCurrent(m_backgroundSurface);
    if (!makeCurrentResult.isSuccess())
    {
        RiveLogE(TAG,
                 "Failed to make background surface current: %s",
                 makeCurrentResult.summary().c_str());
        return makeCurrentResult;
    }

    RiveLogD(TAG, "Making Rive RenderContextGLImpl.");
    m_renderContext = rive::gpu::RenderContextGLImpl::MakeContext();
    if (m_renderContext == nullptr)
    {
        RiveLogE(TAG, "RenderContextGLImpl::MakeContext() returned null.");
        return EGLResult::Failure(EGLResult::FailureOperation::recover,
                                  EGL_BAD_CONTEXT);
    }
    return EGLResult::Ok();
}

void PLSThreadState::teardownPLSResources()
{
    // `PLSThreadState` only owns `m_backgroundSurface`. Window surfaces are
    // owned by `EGLWorkerImpl` and should already be destroyed before this
    // point.
    if (m_currentSurface != EGL_NO_SURFACE &&
        m_currentSurface != m_backgroundSurface)
    {
        RiveLogW(
            TAG,
            "teardownPLSResources() called with a non-background current surface; expected WorkerImpl to destroy it first.");
    }
    m_currentSurface = EGL_NO_SURFACE;
    m_renderContext.reset();

    if (m_backgroundSurface != EGL_NO_SURFACE && m_display != EGL_NO_DISPLAY)
    {
        RiveLogD(TAG, "Destroying background surface.");
        if (!eglDestroySurface(m_display, m_backgroundSurface))
        {
            EGLint error = consume_egl_error_or_default(EGL_BAD_SURFACE, TAG);
            RiveLogW(
                TAG,
                "eglDestroySurface() failed while tearing down background surface: %s",
                EGLErrorString(error).c_str());
        }
    }
    m_backgroundSurface = EGL_NO_SURFACE;
}

void PLSThreadState::destroySurface(EGLSurface eglSurface)
{
    if (eglSurface == EGL_NO_SURFACE)
    {
        RiveLogD(TAG, "Cannot destroy EGL_NO_SURFACE.");
        return;
    }

    assert(eglSurface != m_backgroundSurface);
    if (m_currentSurface == eglSurface)
    {
        RiveLogD(TAG, "Making background surface current.");
        // Make sure m_context always stays current.
        const auto makeCurrentResult = makeCurrent(m_backgroundSurface);
        if (!makeCurrentResult.isSuccess())
        {
            RiveLogE(
                TAG,
                "Failed to switch to background surface before destroy: %s",
                makeCurrentResult.summary().c_str());
        }
    }

    RiveLogD(TAG, "Destroying surface.");
    if (!eglDestroySurface(m_display, eglSurface))
    {
        EGLint error = consume_egl_error_or_default(EGL_BAD_SURFACE, TAG);
        RiveLogW(TAG,
                 "eglDestroySurface() failed while destroying surface: %s",
                 EGLErrorString(error).c_str());
    }
}

EGLResult PLSThreadState::makeCurrent(EGLSurface eglSurface)
{
    if (eglSurface == m_currentSurface)
    {
        RiveLogV(TAG, "Surface is already current.");
        return EGLResult::Ok();
    }

    if (eglSurface == EGL_NO_SURFACE)
    {
        RiveLogE(TAG, "Cannot make EGL_NO_SURFACE current.");
        return EGLResult::Failure(EGLResult::FailureOperation::makeCurrent,
                                  EGL_BAD_SURFACE);
    }
    if (!eglMakeCurrent(m_display, eglSurface, eglSurface, m_context))
    {
        EGLint error = consume_egl_error_or_default(EGL_BAD_CONTEXT, TAG);
        return EGLResult::Failure(EGLResult::FailureOperation::makeCurrent,
                                  error);
    }

    m_currentSurface = eglSurface;
    return EGLResult::Ok();
}

EGLResult PLSThreadState::recoverAfterContextLoss()
{
    RiveLogI(TAG, "Recovering PLS thread state after context loss.");

    teardownPLSResources();

    const EGLResult eglRecoverResult =
        EGLThreadState::recoverAfterContextLoss();
    if (!eglRecoverResult.isSuccess())
    {
        return eglRecoverResult;
    }

    return initializePLSResources();
}
} // namespace rive_android
