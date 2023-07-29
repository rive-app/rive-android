
#include "models/worker_impl.hpp"

namespace rive_android
{
std::unique_ptr<WorkerImpl> WorkerImpl::Make(struct ANativeWindow* window,
                                             EGLThreadState* threadState,
                                             const RendererType type)
{
    bool success;
    std::unique_ptr<WorkerImpl> impl;
    if (type == RendererType::Skia)
    {
        impl = std::make_unique<SkiaWorkerImpl>(window, threadState, &success);
    }
    else
    {
        impl = std::make_unique<PLSWorkerImpl>(window, threadState, &success);
    }
    if (!success)
    {
        impl->destroy(threadState);
        impl.reset();
    }
    return impl;
}

void WorkerImpl::start(jobject ktRenderer, long long timeNs)
{
    auto env = GetJNIEnv();
    jclass ktClass = GetJNIEnv()->GetObjectClass(ktRenderer);
    m_ktRendererClass = reinterpret_cast<jclass>(env->NewWeakGlobalRef(ktClass));
    m_ktDrawCallback = env->GetMethodID(m_ktRendererClass, "draw", "()V");
    m_ktAdvanceCallback = env->GetMethodID(m_ktRendererClass, "advance", "(F)V");
    mLastFrameTimeNs = timeNs;
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
                         EGLThreadState* threadState,
                         jobject ktRenderer,
                         long frameTimeNs)
{
    if (!m_isStarted)
    {
        return;
    }

    float elapsedMs = (frameTimeNs - mLastFrameTimeNs) * 1e-9f;
    mLastFrameTimeNs = frameTimeNs;

    auto env = GetJNIEnv();
    env->CallVoidMethod(ktRenderer, m_ktAdvanceCallback, elapsedMs);

    tracer->beginSection("draw()");

    // Bind context to this thread.
    threadState->makeCurrent(m_eglSurface);
    clear(threadState);
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
void SkiaWorkerImpl::destroy(EGLThreadState* threadState)
{
    m_skRenderer.reset();
    m_skSurface.reset();
    WorkerImpl::destroy(threadState);
}

void SkiaWorkerImpl::clear(EGLThreadState* threadState)
{
    m_skSurface->getCanvas()->clear(uint32_t((0x00000000)));
}

void SkiaWorkerImpl::flush(EGLThreadState* threadState) { m_skSurface->flushAndSubmit(); }

rive::Renderer* SkiaWorkerImpl::renderer() const { return m_skRenderer.get(); }

/* PLSWorkerImpl */
void PLSWorkerImpl::destroy(EGLThreadState* threadState)
{
    m_plsRenderer.reset();
    m_plsRenderTarget.reset();
    WorkerImpl::destroy(threadState);
}

void PLSWorkerImpl::clear(EGLThreadState* threadState)
{
    PLSThreadState* plsThreadState = PLSWorkerImpl::PlsThreadState(threadState);
    rive::pls::PLSRenderContext* plsContext = plsThreadState->plsContext();
    rive::pls::PLSRenderContext::FrameDescriptor frameDescriptor;
    frameDescriptor.renderTarget = m_plsRenderTarget;
    frameDescriptor.loadAction = rive::pls::LoadAction::clear;
    frameDescriptor.clearColor = 0;
    plsContext->beginFrame(std::move(frameDescriptor));
}

void PLSWorkerImpl::flush(EGLThreadState* threadState)
{
    PLSThreadState* plsThreadState = PLSWorkerImpl::PlsThreadState(threadState);
    rive::pls::PLSRenderContext* plsContext = plsThreadState->plsContext();
    plsContext->flush();
    if (m_plsRenderTarget->drawFramebufferID() != 0)
    {
        int w = SizeTTOInt(m_plsRenderTarget->width());
        int h = SizeTTOInt(m_plsRenderTarget->height());
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, m_plsRenderTarget->sideFramebufferID());
        glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }
}

rive::Renderer* PLSWorkerImpl::renderer() const { return m_plsRenderer.get(); }

} // namespace rive_android
