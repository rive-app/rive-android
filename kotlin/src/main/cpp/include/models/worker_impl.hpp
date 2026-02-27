#pragma once

#include <variant>

#include "jni_refs.hpp"
#include "canvas_renderer.hpp"

#include "helpers/thread_state_pls.hpp"

#include "rive/renderer/rive_renderer.hpp"

// std::variant that holds different surface types:
// - ANativeWindow*: for Rive Renderer
// - Kotlin Surface jobject: for the Canvas Renderer
// - std::monostate: for no assigned surface
using SurfaceVariant = std::variant<std::monostate, ANativeWindow*, jobject>;

namespace rive_android
{
/**
 * Per-frame wrapper around EGLResult, including whether a frame was rendered.
 *
 * This is needed because frame execution can abort at multiple stages
 * (`makeCurrent`, draw/flush, `swapBuffers`) and callers need one unified
 * status object to drive render-loop behavior and recovery decisions.
 */
struct WorkerFrameResult
{
    bool didDraw = false;
    EGLResult eglResult = EGLResult::Ok();
};

class WorkerImpl
{
public:
    static std::unique_ptr<WorkerImpl> Make(SurfaceVariant,
                                            DrawableThreadState*,
                                            RendererType);

    virtual ~WorkerImpl()
    {
        // Call destroy() first!
        assert(!m_isStarted);
        RiveLogD("RiveLN/WorkerImpl", "Deleting WorkerImpl.");
    }

    void start(jobject ktRenderer, std::chrono::steady_clock::time_point);

    void stop();

    WorkerFrameResult doFrame(ITracer*,
                              DrawableThreadState*,
                              jobject ktRenderer,
                              std::chrono::steady_clock::time_point);

    virtual EGLResult prepareForDraw(DrawableThreadState*) const = 0;

    virtual void destroy(DrawableThreadState*) = 0;

    virtual void flush(DrawableThreadState*) const = 0;

    [[nodiscard]] virtual rive::Renderer* renderer() const = 0;

protected:
    jclass m_ktRendererClass = nullptr;
    jmethodID m_ktDrawCallback = nullptr;
    jmethodID m_ktAdvanceCallback = nullptr;
    std::chrono::steady_clock::time_point m_lastFrameTime;
    bool m_isStarted = false;
};

class EGLWorkerImpl : public WorkerImpl
{
public:
    ~EGLWorkerImpl() override
    {
        // Call destroy() first!
        assert(m_eglSurface == EGL_NO_SURFACE);
        RiveLogD(TAG, "Deleting EGLWorkerImpl.");
    }

    void destroy(DrawableThreadState* threadState) override
    {
        if (m_eglSurface != EGL_NO_SURFACE)
        {
            RiveLogD(TAG, "Destroying EGL surface.");
            auto eglThreadState = static_cast<EGLThreadState*>(threadState);
            eglThreadState->destroySurface(m_eglSurface);
            m_eglSurface = EGL_NO_SURFACE;
        }
        else
        {
            RiveLogW(TAG, "Surface already destroyed.");
        }
    }

    EGLResult prepareForDraw(DrawableThreadState* threadState) const override
    {
        auto eglThreadState = static_cast<EGLThreadState*>(threadState);
        // Bind context to this thread.
        EGLResult makeCurrentResult = eglThreadState->makeCurrent(m_eglSurface);
        if (!makeCurrentResult.isSuccess())
        {
            return makeCurrentResult;
        }
        clear(threadState);
        return EGLResult::Ok();
    }

    virtual void clear(DrawableThreadState*) const = 0;

protected:
    EGLWorkerImpl(struct ANativeWindow* window,
                  DrawableThreadState* threadState,
                  bool* success)
    {
        RiveLogD(TAG, "Creating EGLWorkerImpl.");
        *success = false;
        auto eglThreadState = static_cast<EGLThreadState*>(threadState);
        m_eglSurface = eglThreadState->createEGLSurface(window);
        if (m_eglSurface == EGL_NO_SURFACE)
        {
            RiveLogE(TAG, "Failed to create EGL surface.");
            return;
        }
        *success = true;
    }

    void* m_eglSurface = EGL_NO_SURFACE;

private:
    static constexpr auto* TAG = "RiveLN/EGLWorkerImpl";
};

class PLSWorkerImpl : public EGLWorkerImpl
{
public:
    PLSWorkerImpl(struct ANativeWindow*, DrawableThreadState*, bool* success);

    void destroy(DrawableThreadState* threadState) override;

    void clear(DrawableThreadState* threadState) const override;

    void flush(DrawableThreadState* threadState) const override;

    [[nodiscard]] rive::Renderer* renderer() const override;

private:
    rive::rcp<rive::gpu::RenderTargetGL> m_renderTarget;

    std::unique_ptr<rive::RiveRenderer> m_plsRenderer;

    // Cast away [threadState] to the the thread state expected by this
    // implementation.
    static PLSThreadState* PlsThreadState(DrawableThreadState* threadState)
    {
        // Quite hacky, but this is a way to sort this out in C++ without
        // RTTI...
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

    ~CanvasWorkerImpl() override
    {
        // Call destroy() first!
        assert(m_ktSurface == nullptr);
    }

    [[nodiscard]] rive::Renderer* renderer() const override
    {
        return m_canvasRenderer.get();
    }

    void flush(DrawableThreadState*) const override;

    EGLResult prepareForDraw(DrawableThreadState*) const override;

    void destroy(DrawableThreadState*) override;

private:
    std::unique_ptr<CanvasRenderer> m_canvasRenderer;
    jobject m_ktSurface = nullptr;
};

} // namespace rive_android
