//
// Created by Umberto Sonnino on 6/27/23.
//
#ifndef RIVE_ANDROID_THREAD_STATE_PLS_HPP
#define RIVE_ANDROID_THREAD_STATE_PLS_HPP

#include <memory>

#include "thread_state_egl.hpp"
#include "rive/pls/gl/pls_render_context_gl_impl.hpp"

namespace rive_android
{
class PLSThreadState : public EGLThreadState
{
public:
    PLSThreadState();
    ~PLSThreadState();

    rive::pls::PLSRenderContext* plsContext() const { return m_plsContext.get(); }

    void destroySurface(EGLSurface eglSurface) override;

    void makeCurrent(EGLSurface eglSurface) override;

private:
    std::unique_ptr<rive::pls::PLSRenderContext> m_plsContext;

    // 1x1 Pbuffer surface that allows us to make the GL context current without a window surface.
    EGLSurface m_backgroundSurface;
};
} // namespace rive_android

#endif // RIVE_ANDROID_THREAD_STATE_PLS_HPP
