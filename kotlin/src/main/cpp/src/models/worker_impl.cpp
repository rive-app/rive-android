#include "models/worker_impl.hpp"

#include "helpers/audio_engine.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "rive/renderer/gl/render_target_gl.hpp"

namespace rive_android
{
constexpr auto* WORKER_TAG = "RiveLN/WorkerImpl";

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
            RiveLogD(WORKER_TAG, "Making Rive WorkerImpl.");
            ANativeWindow* window = std::get<ANativeWindow*>(surface);
            impl =
                std::make_unique<PLSWorkerImpl>(window, threadState, &success);
            break;
        }
        case RendererType::Canvas:
        {
            RiveLogD(WORKER_TAG, "Making Canvas WorkerImpl.");
            jobject ktSurface = std::get<jobject>(surface);
            impl = std::make_unique<CanvasWorkerImpl>(ktSurface, &success);
        }
        default:
            break;
    }
    if (!success)
    {
        RiveLogE(WORKER_TAG, "Failed to make WorkerImpl. Destroying impl.");
        if (impl != nullptr)
        {
            impl->destroy(threadState);
            impl.reset();
        }
    }
    return impl;
}

void WorkerImpl::start(jobject ktRenderer,
                       std::chrono::steady_clock::time_point frameTime)
{
    if (m_isStarted)
    {
        RiveLogV(WORKER_TAG, "WorkerImpl already started.");
        return;
    }
    RiveLogD(WORKER_TAG, "Starting WorkerImpl.");
    auto env = GetJNIEnv();
    jclass ktClass = env->GetObjectClass(ktRenderer);
    m_ktRendererClass =
        reinterpret_cast<jclass>(env->NewWeakGlobalRef(ktClass));
    m_ktDrawCallback = env->GetMethodID(m_ktRendererClass, "draw", "()V");
    m_ktAdvanceCallback =
        env->GetMethodID(m_ktRendererClass, "advance", "(F)V");
    m_lastFrameTime = frameTime;
    m_isStarted = true;
    // Acquire a reference to the audio engine to keep it playing
    AudioEngine::Instance().acquire();
}

void WorkerImpl::stop()
{
    if (!m_isStarted)
    {
        RiveLogV(WORKER_TAG, "WorkerImpl already stopped.");
        return;
    }
    RiveLogD(WORKER_TAG, "Stopping WorkerImpl.");
    // Release the reference to the audio engine to allow it to stop
    AudioEngine::Instance().release();
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

WorkerFrameResult WorkerImpl::doFrame(
    ITracer* tracer,
    DrawableThreadState* threadState,
    jobject ktRenderer,
    std::chrono::steady_clock::time_point frameTime)
{
    WorkerFrameResult result;
    if (!m_isStarted)
    {
        RiveLogW(WORKER_TAG, "Trying to doFrame before WorkerImpl is started.");
        return result;
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

    EGLResult prepareResult = prepareForDraw(threadState);
    if (!prepareResult.isSuccess())
    {
        tracer->endSection(); // draw
        result.eglResult = prepareResult;
        return result;
    }
    // Kotlin callback.
    JNIExceptionHandler::CallVoidMethod(env, ktRenderer, m_ktDrawCallback);

    tracer->beginSection("flush()");
    flush(threadState);
    tracer->endSection(); // flush

    tracer->beginSection("swapBuffers()");
    EGLResult swapResult = threadState->swapBuffers();

    tracer->endSection(); // swapBuffers
    tracer->endSection(); // draw()
    if (!swapResult.isSuccess())
    {
        result.eglResult = swapResult;
        return result;
    }
    result.didDraw = true;
    return result;
}

/* PLSWorkerImpl */
constexpr auto* PLS_TAG = "RiveLN/PLSWorkerImpl";

PLSWorkerImpl::PLSWorkerImpl(struct ANativeWindow* window,
                             DrawableThreadState* threadState,
                             bool* success) :
    EGLWorkerImpl(window, threadState, success)
{
    if (!*success)
    {
        RiveLogE(PLS_TAG, "Failed to make PLS WorkerImpl.");
        return;
    }

    auto eglThreadState = static_cast<EGLThreadState*>(threadState);

    EGLResult makeCurrentResult = eglThreadState->makeCurrent(m_eglSurface);
    if (!makeCurrentResult.isSuccess())
    {
        *success = false;
        RiveLogE(
            PLS_TAG,
            "Failed to make context current while creating PLS WorkerImpl.");
        return;
    }
    rive::gpu::RenderContext* renderContext =
        PLSWorkerImpl::PlsThreadState(eglThreadState)->renderContext();
    if (renderContext == nullptr)
    {
        *success = false;
        RiveLogE(PLS_TAG, "Failed to make Rive Renderer RenderContext.");
        return; // PLS was not supported.
    }
    int width = ANativeWindow_getWidth(window);
    int height = ANativeWindow_getHeight(window);
    GLint sampleCount;
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glGetIntegerv(GL_SAMPLES, &sampleCount);
    RiveLogD(PLS_TAG, "Creating Rive Framebuffer Render Target.");
    m_renderTarget =
        rive::make_rcp<rive::gpu::FramebufferRenderTargetGL>(width,
                                                             height,
                                                             0,
                                                             sampleCount);
    RiveLogD(PLS_TAG, "Creating Rive Renderer.");
    m_plsRenderer = std::make_unique<rive::RiveRenderer>(renderContext);
    *success = true;
}

void PLSWorkerImpl::destroy(DrawableThreadState* threadState)
{
    RiveLogD(PLS_TAG, "Destroying Rive WorkerImpl.");
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
    RiveLogD("RiveLN/CanvasWorkerImpl", "Destroying Canvas WorkerImpl.");
    assert(m_ktSurface != nullptr);

    m_canvasRenderer.reset();
    GetJNIEnv()->DeleteGlobalRef(m_ktSurface);
    m_ktSurface = nullptr;
}

EGLResult CanvasWorkerImpl::prepareForDraw(DrawableThreadState*) const
{
    m_canvasRenderer->bindCanvas(m_ktSurface);
    return EGLResult::Ok();
}

void CanvasWorkerImpl::flush(DrawableThreadState*) const
{
    m_canvasRenderer->unlockAndPost(m_ktSurface);
}
} // namespace rive_android
