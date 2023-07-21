//
// Created by Umberto Sonnino on 6/27/23.
//
#ifndef RIVE_ANDROID_THREAD_STATE_PLS_HPP
#define RIVE_ANDROID_THREAD_STATE_PLS_HPP

#include <memory>

#include "thread_state_egl.hpp"
#include "rive/pls/gl/pls_render_context_gl.hpp"

namespace rive_android
{
class PLSThreadState : public EGLThreadState
{
public:
    PLSThreadState() = default;

    ~PLSThreadState() { releaseContext(); }

    rive::pls::PLSRenderContextGL* plsContext() const { return m_plsContext.get(); }

    void destroySurface(EGLSurface eglSurface) override;

    void makeCurrent(EGLSurface eglSurface) override;

protected:
    void releaseContext() override;

private:
    std::unique_ptr<rive::pls::PLSRenderContextGL> m_plsContext;

    bool m_ownsCurrentSurface = false;
};
} // namespace rive_android

#endif // RIVE_ANDROID_THREAD_STATE_PLS_HPP
