#include "models/worker_impl.hpp"

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
}

void WorkerImpl::stop()
{
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
void PLSWorkerImpl::destroy(DrawableThreadState* threadState)
{
    m_plsRenderer.reset();
    m_plsRenderTarget.reset();
    EGLWorkerImpl::destroy(threadState);
}

void PLSWorkerImpl::clear(DrawableThreadState* threadState) const
{
    PLSThreadState* plsThreadState = PLSWorkerImpl::PlsThreadState(threadState);
    rive::pls::PLSRenderContext* plsContext = plsThreadState->plsContext();
    rive::pls::PLSRenderContext::FrameDescriptor frameDescriptor;
    frameDescriptor.renderTarget = m_plsRenderTarget;
    frameDescriptor.loadAction = rive::pls::LoadAction::clear;
    frameDescriptor.clearColor = 0;
    plsContext->beginFrame(std::move(frameDescriptor));
}

void PLSWorkerImpl::flush(DrawableThreadState* threadState) const
{
    PLSThreadState* plsThreadState = PLSWorkerImpl::PlsThreadState(threadState);
    rive::pls::PLSRenderContext* plsContext = plsThreadState->plsContext();
    plsContext->flush();
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
