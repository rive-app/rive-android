#include "helpers/thread_state_pls.hpp"

#include "helpers/rive_log.hpp"

namespace rive_android
{
constexpr auto* TAG = "RiveLN/PLSThreadState";

PLSThreadState::PLSThreadState()
{
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
    EGL_ERR_CHECK();
    if (m_backgroundSurface == EGL_NO_SURFACE)
    {
        RiveLogE(TAG,
                 "Failed to create a 1x1 background Pbuffer surface for PLS.");
    }

    RiveLogD(TAG, "Making background surface current.");
    eglMakeCurrent(m_display,
                   m_backgroundSurface,
                   m_backgroundSurface,
                   m_context);
    m_currentSurface = m_backgroundSurface;

    RiveLogD(TAG, "Making Rive RenderContextGLImpl.");
    m_renderContext = rive::gpu::RenderContextGLImpl::MakeContext();
}

PLSThreadState::~PLSThreadState()
{
    RiveLogD(TAG, "Deleting PLS thread state.");
    assert(m_currentSurface == m_backgroundSurface);
    m_renderContext.reset();

    RiveLogD(TAG, "Destroying background surface.");
    eglDestroySurface(m_display, m_backgroundSurface);
    EGL_ERR_CHECK();
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
        makeCurrent(m_backgroundSurface);
    }

    RiveLogD(TAG, "Destroying surface.");
    eglDestroySurface(m_display, eglSurface);
    EGL_ERR_CHECK();
}

void PLSThreadState::makeCurrent(EGLSurface eglSurface)
{
    if (eglSurface == m_currentSurface)
    {
        RiveLogV(TAG, "Surface is already current.");
        return;
    }

    if (eglSurface == EGL_NO_SURFACE)
    {
        RiveLogE(TAG, "Cannot make EGL_NO_SURFACE current.");
        return;
    }
    eglMakeCurrent(m_display, eglSurface, eglSurface, m_context);

    m_currentSurface = eglSurface;
    EGL_ERR_CHECK();
}
} // namespace rive_android
