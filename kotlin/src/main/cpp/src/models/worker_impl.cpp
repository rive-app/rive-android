#include "models/worker_impl.hpp"

#include "rive/renderer/gl/render_target_gl.hpp"
#include "rive/audio/audio_engine.hpp"

namespace rive_android
{

std::unique_ptr<WorkerImpl> WorkerImpl::Make(SurfaceVariant surface,
                                             DrawableThreadState* threadState,
                                             const RendererType type)
{
    assert(surface.index() > 0); // Valid surface?

    bool success = false;
    std::unique_ptr<WorkerImpl> impl;
    switch (type)
    {
        case RendererType::Rive:
        {
            ANativeWindow* window = std::get<ANativeWindow*>(surface);
            impl = std::make_unique<PLSWorkerImpl>(window, threadState, &success);
            break;
        }
        case RendererType::Skia:
        {
            ANativeWindow* window = std::get<ANativeWindow*>(surface);
            impl = std::make_unique<SkiaWorkerImpl>(window, threadState, &success);
            break;
        }
        case RendererType::Canvas:
        {
            jobject ktSurface = std::get<jobject>(surface);
            impl = std::make_unique<CanvasWorkerImpl>(ktSurface, &success);
        }
        default:
            break;
    }
    if (!success)
    {
        impl->destroy(threadState);
        impl.reset();
    }
    return impl;
}

void WorkerImpl::start(jobject ktRenderer, std::chrono::high_resolution_clock::time_point frameTime)
{
    auto env = GetJNIEnv();
    jclass ktClass = env->GetObjectClass(ktRenderer);
    m_ktRendererClass = reinterpret_cast<jclass>(env->NewWeakGlobalRef(ktClass));
    m_ktDrawCallback = env->GetMethodID(m_ktRendererClass, "draw", "()V");
    m_ktAdvanceCallback = env->GetMethodID(m_ktRendererClass, "advance", "(F)V");
    m_lastFrameTime = frameTime;
    m_isStarted = true;
    if (auto engine = rive::AudioEngine::RuntimeEngine(false))
    {
        engine->start();
    }
}

void WorkerImpl::stop()
{
    if (auto engine = rive::AudioEngine::RuntimeEngine(false))
    {
        engine->stop();
    }
    auto env = GetJNIEnv();
    if (m_ktRendererClass != nullptr)
    {
        env->DeleteWeakGlobalRef(m_ktRendererClass);
    }
    m_ktRendererClass = nullptr;
    m_ktDrawCallback = nullptr;
    m_ktAdvanceCallback = nullptr;
    m_isStarted = false;
}

void WorkerImpl::doFrame(ITracer* tracer,
                         DrawableThreadState* threadState,
                         jobject ktRenderer,
                         std::chrono::high_resolution_clock::time_point frameTime)
{
    if (!m_isStarted)
    {
        return;
    }

    float fElapsedMs = std::chrono::duration<float>(frameTime - m_lastFrameTime).count();
    m_lastFrameTime = frameTime;

    auto env = GetJNIEnv();
    env->CallVoidMethod(ktRenderer, m_ktAdvanceCallback, fElapsedMs);

    tracer->beginSection("draw()");

    prepareForDraw(threadState);
    // Kotlin callback.
    env->CallVoidMethod(ktRenderer, m_ktDrawCallback);

    tracer->beginSection("flush()");
    flush(threadState);
    tracer->endSection(); // flush

    tracer->beginSection("swapBuffers()");
    threadState->swapBuffers();

    tracer->endSection(); // swapBuffers
    tracer->endSection(); // draw()
}

/* SkiaWorkerImpl */
void SkiaWorkerImpl::destroy(DrawableThreadState* threadState)
{
    m_skRenderer.reset();
    m_skSurface.reset();
    EGLWorkerImpl::destroy(threadState);
}

void SkiaWorkerImpl::clear(DrawableThreadState*) const
{
    m_skSurface->getCanvas()->clear(uint32_t((0x00000000)));
}

void SkiaWorkerImpl::flush(DrawableThreadState* threadState) const
{
    m_skSurface->flushAndSubmit();
}

rive::Renderer* SkiaWorkerImpl::renderer() const { return m_skRenderer.get(); }

/* PLSWorkerImpl */
PLSWorkerImpl::PLSWorkerImpl(struct ANativeWindow* window,
                             DrawableThreadState* threadState,
                             bool* success) :
    EGLWorkerImpl(window, threadState, success)
{
    if (!success)
    {
        return;
    }

    auto eglThreadState = static_cast<EGLThreadState*>(threadState);

    eglThreadState->makeCurrent(m_eglSurface);
    rive::gpu::RenderContext* plsContext =
        PLSWorkerImpl::PlsThreadState(eglThreadState)->plsContext();
    if (plsContext == nullptr)
    {
        return; // PLS was not supported.
    }
    int width = ANativeWindow_getWidth(window);
    int height = ANativeWindow_getHeight(window);
    GLint sampleCount;
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glGetIntegerv(GL_SAMPLES, &sampleCount);
    m_plsRenderTarget =
        rive::make_rcp<rive::gpu::FramebufferRenderTargetGL>(width, height, 0, sampleCount);
    m_plsRenderer = std::make_unique<rive::gpu::RiveRenderer>(plsContext);
    *success = true;
}

void PLSWorkerImpl::destroy(DrawableThreadState* threadState)
{
    m_plsRenderer.reset();
    m_plsRenderTarget.reset();
    EGLWorkerImpl::destroy(threadState);
}

void PLSWorkerImpl::clear(DrawableThreadState* threadState) const
{
    PLSThreadState* plsThreadState = PLSWorkerImpl::PlsThreadState(threadState);
    rive::gpu::RenderContext* plsContext = plsThreadState->plsContext();
    plsContext->beginFrame({
        .renderTargetWidth = m_plsRenderTarget->width(),
        .renderTargetHeight = m_plsRenderTarget->height(),
        .loadAction = rive::gpu::LoadAction::clear,
        .clearColor = 0,
    });
}

void PLSWorkerImpl::flush(DrawableThreadState* threadState) const
{
    PLSThreadState* plsThreadState = PLSWorkerImpl::PlsThreadState(threadState);
    rive::gpu::RenderContext* plsContext = plsThreadState->plsContext();
    plsContext->flush({.renderTarget = m_plsRenderTarget.get()});
}

rive::Renderer* PLSWorkerImpl::renderer() const { return m_plsRenderer.get(); }

/* CanvasWorkerImpl */
void CanvasWorkerImpl::destroy(DrawableThreadState*)
{
    assert(m_ktSurface != nullptr);

    m_canvasRenderer.reset();
    GetJNIEnv()->DeleteGlobalRef(m_ktSurface);
    m_ktSurface = nullptr;
}

void CanvasWorkerImpl::prepareForDraw(DrawableThreadState*) const
{
    m_canvasRenderer->bindCanvas(m_ktSurface);
}

void CanvasWorkerImpl::flush(DrawableThreadState*) const
{
    m_canvasRenderer->unlockAndPost(m_ktSurface);
}
} // namespace rive_android
