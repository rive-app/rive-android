#include "helpers/jni_exception_handler.hpp"
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
            impl =
                std::make_unique<PLSWorkerImpl>(window, threadState, &success);
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

void WorkerImpl::start(jobject ktRenderer,
                       std::chrono::high_resolution_clock::time_point frameTime)
{
    auto env = GetJNIEnv();
    jclass ktClass = env->GetObjectClass(ktRenderer);
    m_ktRendererClass =
        reinterpret_cast<jclass>(env->NewWeakGlobalRef(ktClass));
    m_ktDrawCallback = env->GetMethodID(m_ktRendererClass, "draw", "()V");
    m_ktAdvanceCallback =
        env->GetMethodID(m_ktRendererClass, "advance", "(F)V");
    m_lastFrameTime = frameTime;
    m_isStarted = true;
    // Conditional from CMake on whether to include miniaudio
#ifdef WITH_AUDIO
    if (auto engine = rive::AudioEngine::RuntimeEngine(false))
    {
        engine->start();
    }
#endif
}

void WorkerImpl::stop()
{
    // Conditional from CMake on whether to include miniaudio
#ifdef WITH_RIVE_AUDIO
    if (auto engine = rive::AudioEngine::RuntimeEngine(false))
    {
        engine->stop();
    }
#endif
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

void WorkerImpl::doFrame(
    ITracer* tracer,
    DrawableThreadState* threadState,
    jobject ktRenderer,
    std::chrono::high_resolution_clock::time_point frameTime)
{
    if (!m_isStarted)
    {
        return;
    }

    float fElapsedMs =
        std::chrono::duration<float>(frameTime - m_lastFrameTime).count();
    m_lastFrameTime = frameTime;

    auto env = GetJNIEnv();
    JNIExceptionHandler::CallVoidMethod(env,
                                        ktRenderer,
                                        m_ktAdvanceCallback,
                                        fElapsedMs);

    tracer->beginSection("draw()");

    prepareForDraw(threadState);
    // Kotlin callback.
    JNIExceptionHandler::CallVoidMethod(env, ktRenderer, m_ktDrawCallback);

    tracer->beginSection("flush()");
    flush(threadState);
    tracer->endSection(); // flush

    tracer->beginSection("swapBuffers()");
    threadState->swapBuffers();

    tracer->endSection(); // swapBuffers
    tracer->endSection(); // draw()
}

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
    rive::gpu::RenderContext* renderContext =
        PLSWorkerImpl::PlsThreadState(eglThreadState)->renderContext();
    if (renderContext == nullptr)
    {
        return; // PLS was not supported.
    }
    int width = ANativeWindow_getWidth(window);
    int height = ANativeWindow_getHeight(window);
    GLint sampleCount;
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glGetIntegerv(GL_SAMPLES, &sampleCount);
    m_renderTarget =
        rive::make_rcp<rive::gpu::FramebufferRenderTargetGL>(width,
                                                             height,
                                                             0,
                                                             sampleCount);
    m_plsRenderer = std::make_unique<rive::RiveRenderer>(renderContext);
    *success = true;
}

void PLSWorkerImpl::destroy(DrawableThreadState* threadState)
{
    m_plsRenderer.reset();
    m_renderTarget.reset();
    EGLWorkerImpl::destroy(threadState);
}

void PLSWorkerImpl::clear(DrawableThreadState* threadState) const
{
    PLSThreadState* plsThreadState = PLSWorkerImpl::PlsThreadState(threadState);
    rive::gpu::RenderContext* renderContext = plsThreadState->renderContext();
    renderContext->beginFrame({
        .renderTargetWidth = m_renderTarget->width(),
        .renderTargetHeight = m_renderTarget->height(),
        .loadAction = rive::gpu::LoadAction::clear,
        .clearColor = 0,
    });
}

void PLSWorkerImpl::flush(DrawableThreadState* threadState) const
{
    PLSThreadState* plsThreadState = PLSWorkerImpl::PlsThreadState(threadState);
    rive::gpu::RenderContext* renderContext = plsThreadState->renderContext();
    renderContext->flush({.renderTarget = m_renderTarget.get()});
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
