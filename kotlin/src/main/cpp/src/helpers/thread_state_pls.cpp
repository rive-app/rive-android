//
// Created by Umberto Sonnino on 7/13/23.
//
#include "helpers/thread_state_pls.hpp"

namespace rive_android
{
PLSThreadState::PLSThreadState()
{
    // Create a 1x1 Pbuffer surface that we can use to guarantee m_context is always current on this
    // thread.
    const EGLint PbufferAttrs[] = {
        EGL_WIDTH,
        1,
        EGL_HEIGHT,
        1,
        EGL_NONE,
    };
    m_backgroundSurface = eglCreatePbufferSurface(m_display, m_config, PbufferAttrs);
    EGL_ERR_CHECK();
    if (m_backgroundSurface == EGL_NO_SURFACE)
    {
        LOGE("Failed to create a 1x1 background Pbuffer surface for PLS");
    }

    eglMakeCurrent(m_display, m_backgroundSurface, m_backgroundSurface, m_context);
    m_currentSurface = m_backgroundSurface;

    m_plsContext = rive::gpu::PLSRenderContextGLImpl::MakeContext();
}

PLSThreadState::~PLSThreadState()
{
    assert(m_currentSurface == m_backgroundSurface);
    m_plsContext.reset();

    eglDestroySurface(m_display, m_backgroundSurface);
    EGL_ERR_CHECK();
}

void PLSThreadState::destroySurface(EGLSurface eglSurface)
{
    if (eglSurface == EGL_NO_SURFACE)
    {
        return;
    }

    assert(eglSurface != m_backgroundSurface);
    if (m_currentSurface == eglSurface)
    {
        // Make sure m_context always stays current.
        makeCurrent(m_backgroundSurface);
    }

    eglDestroySurface(m_display, eglSurface);
    EGL_ERR_CHECK();
}

void PLSThreadState::makeCurrent(EGLSurface eglSurface)
{
    if (eglSurface == m_currentSurface)
    {
        return;
    }

    if (eglSurface == EGL_NO_SURFACE)
    {
        LOGE("Cannot make EGL_NO_SURFACE current");
        return;
    }
    eglMakeCurrent(m_display, eglSurface, eglSurface, m_context);

    m_currentSurface = eglSurface;
    EGL_ERR_CHECK();
}
} // namespace rive_android
