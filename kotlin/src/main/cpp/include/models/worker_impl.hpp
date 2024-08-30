//
// Created by Umberto Sonnino on 7/10/23.
//

#ifndef RIVE_ANDROID_WORKER_IMPL_HPP
#define RIVE_ANDROID_WORKER_IMPL_HPP

#include <variant>

#include <skia_renderer.hpp>

#include "jni_refs.hpp"

#include "helpers/general.hpp"
#include "helpers/thread_state_egl.hpp"
#include "helpers/thread_state_pls.hpp"
#include "helpers/thread_state_skia.hpp"
#include "helpers/worker_ref.hpp"
#include "helpers/worker_thread.hpp"

#include "models/dimensions_helper.hpp"

#include "rive/renderer/render_context.hpp"
#include "rive/renderer/rive_renderer.hpp"

#include "canvas_renderer.hpp"

#include "SkSurface.h"

// Either ANativeWindow* or a Kotlin Surface.
// std::monostate holds an 'empty' variant.
using SurfaceVariant = std::variant<std::monostate, ANativeWindow*, jobject>;

namespace rive_android
{

class WorkerImpl
{
public:
    static std::unique_ptr<WorkerImpl> Make(SurfaceVariant,
                                            DrawableThreadState*,
                                            const RendererType);

    virtual ~WorkerImpl()
    {
        // Call destroy() first!
        assert(!m_isStarted);
    }

    void start(jobject ktRenderer, std::chrono::high_resolution_clock::time_point);

    void stop();

    void doFrame(ITracer*,
                 DrawableThreadState*,
                 jobject ktRenderer,
                 std::chrono::high_resolution_clock::time_point);

    virtual void prepareForDraw(DrawableThreadState*) const = 0;

    virtual void destroy(DrawableThreadState*) = 0;

    virtual void flush(DrawableThreadState*) const = 0;

    virtual rive::Renderer* renderer() const = 0;

protected:
    jclass m_ktRendererClass = nullptr;
    jmethodID m_ktDrawCallback = nullptr;
    jmethodID m_ktAdvanceCallback = nullptr;
    std::chrono::high_resolution_clock::time_point m_lastFrameTime;
    bool m_isStarted = false;
};

class EGLWorkerImpl : public WorkerImpl
{
public:
    virtual ~EGLWorkerImpl()
    {
        // Call destroy() first!
        assert(m_eglSurface == EGL_NO_SURFACE);
    }

    virtual void destroy(DrawableThreadState* threadState) override
    {
        if (m_eglSurface != EGL_NO_SURFACE)
        {
            auto eglThreadState = static_cast<EGLThreadState*>(threadState);
            eglThreadState->destroySurface(m_eglSurface);
            m_eglSurface = EGL_NO_SURFACE;
        }
    }

    virtual void prepareForDraw(DrawableThreadState* threadState) const override
    {
        auto eglThreadState = static_cast<EGLThreadState*>(threadState);
        // Bind context to this thread.
        eglThreadState->makeCurrent(m_eglSurface);
        clear(threadState);
    }

    virtual void clear(DrawableThreadState*) const = 0;

protected:
    EGLWorkerImpl(struct ANativeWindow* window, DrawableThreadState* threadState, bool* success)
    {
        *success = false;
        auto eglThreadState = static_cast<EGLThreadState*>(threadState);
        m_eglSurface = eglThreadState->createEGLSurface(window);
        if (m_eglSurface == EGL_NO_SURFACE)
            return;
        *success = true;
    }

    void* m_eglSurface = EGL_NO_SURFACE;
};

class SkiaWorkerImpl : public EGLWorkerImpl
{
public:
    SkiaWorkerImpl(struct ANativeWindow* window, DrawableThreadState* threadState, bool* success) :
        EGLWorkerImpl(window, threadState, success)
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

    void destroy(DrawableThreadState* threadState) override;

    void clear(DrawableThreadState* threadState) const override;

    void flush(DrawableThreadState* threadState) const override;

    rive::Renderer* renderer() const override;

protected:
    sk_sp<SkSurface> m_skSurface;
    std::unique_ptr<rive::SkiaRenderer> m_skRenderer;
};

class PLSWorkerImpl : public EGLWorkerImpl
{
public:
    PLSWorkerImpl(struct ANativeWindow*, DrawableThreadState*, bool* success);

    void destroy(DrawableThreadState* threadState) override;

    void clear(DrawableThreadState* threadState) const override;

    void flush(DrawableThreadState* threadState) const override;

    rive::Renderer* renderer() const override;

private:
    rive::rcp<rive::gpu::RenderTargetGL> m_renderTarget;

    std::unique_ptr<rive::RiveRenderer> m_plsRenderer;

    // Cast away [threadState] to the the thread state expected by this implementation.
    static PLSThreadState* PlsThreadState(DrawableThreadState* threadState)
    {
        // Quite hacky, but this is a way to sort this out in C++ without RTTI...
        return static_cast<PLSThreadState*>(threadState);
    }
};

class CanvasWorkerImpl : public WorkerImpl
{
public:
    CanvasWorkerImpl(jobject ktSurface, bool* success) :
        m_canvasRenderer{std::make_unique<CanvasRenderer>()}
    {
        m_ktSurface = GetJNIEnv()->NewGlobalRef(ktSurface);
        *success = true;
    }

    ~CanvasWorkerImpl()
    {
        // Call destroy() first!
        assert(m_ktSurface == nullptr);
    }

    rive::Renderer* renderer() const override { return m_canvasRenderer.get(); }

    void flush(DrawableThreadState*) const override;

    void prepareForDraw(DrawableThreadState*) const override;

    void destroy(DrawableThreadState*) override;

private:
    std::unique_ptr<CanvasRenderer> m_canvasRenderer;
    jobject m_ktSurface = nullptr;
};

} // namespace rive_android
#endif // RIVE_ANDROID_WORKER_IMPL_HPP
