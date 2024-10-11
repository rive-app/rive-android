//
// Created by Umberto Sonnino on 6/27/23.
//
#ifndef RIVE_ANDROID_THREAD_STATE_PLS_HPP
#define RIVE_ANDROID_THREAD_STATE_PLS_HPP

#include <memory>

#include "thread_state_egl.hpp"
#include "rive/renderer/gl/render_context_gl_impl.hpp"

namespace rive_android
{
class PLSThreadState : public EGLThreadState
{
public:
    PLSThreadState();
    ~PLSThreadState();

    rive::gpu::RenderContext* renderContext() const
    {
        return m_renderContext.get();
    }

    void destroySurface(EGLSurface eglSurface) override;

    void makeCurrent(EGLSurface eglSurface) override;

private:
    std::unique_ptr<rive::gpu::RenderContext> m_renderContext;

    // 1x1 Pbuffer surface that allows us to make the GL context current without
    // a window surface.
    EGLSurface m_backgroundSurface;
};
} // namespace rive_android

#endif // RIVE_ANDROID_THREAD_STATE_PLS_HPP
