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
    PLSThreadState() = default;

    ~PLSThreadState() { releaseContext(); }

    rive::pls::PLSRenderContext* plsContext() const { return m_plsContext.get(); }
    rive::pls::PLSRenderContextGLImpl* plsContextImpl() const { return m_plsImpl.get(); }

    void destroySurface(EGLSurface eglSurface) override;

    void makeCurrent(EGLSurface eglSurface) override;

protected:
    void releaseContext() override;

private:
    rive::rcp<rive::pls::PLSRenderContextGLImpl> m_plsImpl;
    std::unique_ptr<rive::pls::PLSRenderContext> m_plsContext;

    bool m_ownsCurrentSurface = false;
};
} // namespace rive_android

#endif // RIVE_ANDROID_THREAD_STATE_PLS_HPP
