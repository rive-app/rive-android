//
// Created by Umberto Sonnino on 7/10/23.
//

#ifndef RIVE_ANDROID_WORKER_IMPL_HPP
#define RIVE_ANDROID_WORKER_IMPL_HPP

#include <skia_renderer.hpp>

#include "jni_refs.hpp"

#include "helpers/thread_state_egl.hpp"
#include "helpers/thread_state_skia.hpp"
#include "helpers/egl_worker.hpp"
#include "helpers/general.hpp"
#include "helpers/thread_state_pls.hpp"
#include "helpers/worker_thread.hpp"

#include "models/dimensions_helper.hpp"

#include "rive/pls/pls_render_context.hpp"
#include "rive/pls/pls_renderer.hpp"

#include "SkSurface.h"

namespace rive_android
{
class WorkerImpl
{
public:
    static std::unique_ptr<WorkerImpl> Make(struct ANativeWindow* window,
                                            EGLThreadState* threadState,
                                            const RendererType type);

    virtual ~WorkerImpl()
    {
        // Call destroy() first!
        assert(!m_isStarted);
        assert(m_eglSurface == EGL_NO_SURFACE);
    }

    virtual void destroy(EGLThreadState* threadState)
    {
        if (m_eglSurface != EGL_NO_SURFACE)
        {
            threadState->destroySurface(m_eglSurface);
            m_eglSurface = EGL_NO_SURFACE;
        }
    }

    void start(jobject ktRenderer, std::chrono::high_resolution_clock::time_point frameTime);

    void stop();

    void doFrame(ITracer* tracer,
                 EGLThreadState* threadState,
                 jobject ktRenderer,
                 std::chrono::high_resolution_clock::time_point frameTime);

    virtual void clear(EGLThreadState* threadState) = 0;

    virtual void flush(EGLThreadState* threadState) = 0;

    virtual rive::Renderer* renderer() const = 0;

protected:
    WorkerImpl(struct ANativeWindow* window, EGLThreadState* threadState, bool* success)
    {
        *success = false;
        m_eglSurface = threadState->createEGLSurface(window);
        if (m_eglSurface == EGL_NO_SURFACE)
            return;
        *success = true;
    }

    void* m_eglSurface = EGL_NO_SURFACE;

    jclass m_ktRendererClass = nullptr;
    jmethodID m_ktDrawCallback = nullptr;
    jmethodID m_ktAdvanceCallback = nullptr;
    std::chrono::high_resolution_clock::time_point m_lastFrameTime;
    bool m_isStarted = false;
};

class SkiaWorkerImpl : public WorkerImpl
{
public:
    SkiaWorkerImpl(struct ANativeWindow* window, EGLThreadState* threadState, bool* success) :
        WorkerImpl(window, threadState, success)
    {
        if (!success)
        {
            return;
        }
        m_skSurface = static_cast<SkiaThreadState*>(threadState)
                          ->createSkiaSurface(m_eglSurface,
                                              ANativeWindow_getWidth(window),
                                              ANativeWindow_getHeight(window));
        if (m_skSurface == nullptr)
            return;
        m_skRenderer = std::make_unique<rive::SkiaRenderer>(m_skSurface->getCanvas());
    }

    void destroy(EGLThreadState* threadState) override;

    void clear(EGLThreadState* threadState) override;

    void flush(EGLThreadState* threadState) override;

    rive::Renderer* renderer() const override;

protected:
    sk_sp<SkSurface> m_skSurface;
    std::unique_ptr<rive::SkiaRenderer> m_skRenderer;
};

class PLSWorkerImpl : public WorkerImpl
{
public:
    PLSWorkerImpl(struct ANativeWindow* window, EGLThreadState* threadState, bool* success) :
        WorkerImpl(window, threadState, success)
    {
        if (!success)
        {
            return;
        }

        threadState->makeCurrent(m_eglSurface);
        PLSThreadState* plsThreadState = PLSWorkerImpl::PlsThreadState(threadState);
        auto plsContextImpl =
            plsThreadState->plsContext()->static_impl_cast<rive::pls::PLSRenderContextGLImpl>();
        int width = ANativeWindow_getWidth(window);
        int height = ANativeWindow_getHeight(window);
        m_plsRenderTarget = plsContextImpl->wrapGLRenderTarget(0, width, height);
        if (m_plsRenderTarget == nullptr)
        {
            m_plsRenderTarget = plsContextImpl->makeOffscreenRenderTarget(width, height);
        }
        if (m_plsRenderTarget == nullptr)
        {
            return;
        }
        m_plsRenderer = std::make_unique<rive::pls::PLSRenderer>(plsThreadState->plsContext());
        *success = true;
    }

    void destroy(EGLThreadState* threadState) override;

    void clear(EGLThreadState* threadState) override;

    void flush(EGLThreadState* threadState) override;

    rive::Renderer* renderer() const override;

private:
    rive::rcp<rive::pls::PLSRenderTargetGL> m_plsRenderTarget;

    std::unique_ptr<rive::pls::PLSRenderer> m_plsRenderer;

    // Cast away [threadState] to the the thread state expected by this implementation.
    static PLSThreadState* PlsThreadState(EGLThreadState* threadState)
    {
        // Quite hacky, but this is a way to sort this out in C++ without RTTI...
        return static_cast<PLSThreadState*>(threadState);
    }
};
} // namespace rive_android
#endif // RIVE_ANDROID_WORKER_IMPL_HPP
