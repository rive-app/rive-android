#pragma once

#include <memory>

#include "thread_state_egl.hpp"
#include "rive/renderer/gl/render_context_gl_impl.hpp"

namespace rive_android
{
class PLSThreadState : public EGLThreadState
{
public:
    PLSThreadState();
    ~PLSThreadState() override;

    [[nodiscard]] rive::gpu::RenderContext* renderContext() const
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
