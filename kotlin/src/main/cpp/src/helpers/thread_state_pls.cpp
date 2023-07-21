//
// Created by Umberto Sonnino on 7/13/23.
//
#include "helpers/thread_state_pls.hpp"

namespace rive_android
{
void PLSThreadState::destroySurface(EGLSurface eglSurface)
{
    if (eglSurface == EGL_NO_SURFACE)
    {
        return;
    }

    if (m_currentSurface == eglSurface)
    {
        m_ownsCurrentSurface = true;
        return;
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

    if (m_ownsCurrentSurface)
    {
        eglDestroySurface(m_display, m_currentSurface);
        EGL_ERR_CHECK();
        m_ownsCurrentSurface = false;
    }
    m_currentSurface = eglSurface;
    EGL_ERR_CHECK();

    if (m_plsContext == nullptr)
    {
        m_plsContext = rive::pls::PLSRenderContextGL::Make();
    }
}

void PLSThreadState::releaseContext()
{
    if (m_plsContext.get())
    {
        assert(m_currentSurface != EGL_NO_SURFACE);
        m_plsContext.reset();
    }

    if (m_ownsCurrentSurface)
    {
        eglDestroySurface(m_display, m_currentSurface);
        EGL_ERR_CHECK();
    }
}

} // namespace rive_android
