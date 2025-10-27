#include "models/jni_renderer.hpp"

using namespace std::chrono_literals;

namespace rive_android
{
JNIRenderer::JNIRenderer(
    jobject ktRenderer,
    bool trace /* = false */,
    const RendererType rendererType /* = RendererType::Canvas */) :
    m_worker(RefWorker::CurrentOrFallback(rendererType)),
    // Grab a Global Ref to prevent Garbage Collection to clean up the object
    //  from under us since the destructor will be called from the render thread
    //  rather than the UI thread.
    m_ktRenderer(GetJNIEnv()->NewGlobalRef(ktRenderer)),
    m_tracer(makeTracer(trace))
{}

JNIRenderer::~JNIRenderer()
{
    // This destructor is just a final sanity check: all heavy lifting is done
    // in the lambda inside `scheduleDisposal()`
    // We assert here to ensure that the asynchronous disposal path was taken.
    assert(m_isDisposeScheduled &&
           "JNIRenderer was deleted directly instead of scheduling disposal!");
}

void JNIRenderer::scheduleDispose()
{
    bool isDisposed = m_isDisposeScheduled.exchange(true);
    assert(!isDisposed);

    // The lambda captures `this` to operate on the JNIRenderer instance.
    // This task is queued on the worker thread. The UI thread does not wait.
    m_worker->run([this](DrawableThreadState* threadState) {
        if (m_workerImpl)
        {
            m_workerImpl->destroy(threadState);
        }

        auto env = GetJNIEnv();
        jclass ktClass = env->GetObjectClass(m_ktRenderer);
        auto disposeDeps =
            env->GetMethodID(ktClass, "disposeDependencies", "()V");
        env->CallVoidMethod(m_ktRenderer, disposeDeps);
        env->DeleteGlobalRef(m_ktRenderer);
        releaseSurface(&m_surface);

        delete this;
    });
}

void JNIRenderer::setSurface(SurfaceVariant surface)
{
    if (m_isDisposeScheduled)
    {
        return;
    }
    SurfaceVariant oldSurface = m_surface;
    acquireSurface(surface);
    m_worker->run([this,
                   /**
                    * Explicitly capture `oldSurface` here to ensure that the
                    * lambda has the right value.Since our inner functions
                    * require a non-const pointer, make this lambda mutable
                    *
                    */
                   oldSurface](DrawableThreadState* threadState) mutable {
        m_workerThreadID = std::this_thread::get_id();
        if (m_workerImpl)
        {
            m_workerImpl->destroy(threadState);
            m_workerImpl.reset();
            releaseSurface(&oldSurface);
        }
        if (m_surface.index() > 0)
        {
            m_workerImpl = WorkerImpl::Make(m_surface,
                                            threadState,
                                            m_worker->rendererType());
        }
    });
}

rive::Renderer* JNIRenderer::getRendererOnWorkerThread() const
{
    assert(std::this_thread::get_id() == m_workerThreadID);
    if (std::this_thread::get_id() != m_workerThreadID)
        return nullptr;
    if (!m_workerImpl)
        return nullptr;
    return m_workerImpl->renderer();
}

void JNIRenderer::start()
{
    if (m_isDisposeScheduled)
    {
        return;
    }
    m_worker->run([this](DrawableThreadState* threadState) {
        if (!m_workerImpl)
            return;
        auto now = std::chrono::steady_clock::now();
        m_fpsLastFrameTime = now;
        m_workerImpl->start(m_ktRenderer, now);
    });
}

void JNIRenderer::stop()
{
    if (m_isDisposeScheduled)
    {
        return;
    }
    m_worker->run([this](DrawableThreadState* threadState) {
        if (!m_workerImpl)
            return;
        m_workerImpl->stop();
    });
}

void JNIRenderer::doFrame()
{
    if (m_isDisposeScheduled)
    {
        return;
    }

    if (m_numScheduledFrames >= kMaxScheduledFrames)
    {
        return;
    }

    m_worker->run([this](DrawableThreadState* threadState) {
        if (!m_workerImpl)
            return;
        auto now = std::chrono::high_resolution_clock::now();
        m_workerImpl->doFrame(m_tracer.get(), threadState, m_ktRenderer, now);
        m_numScheduledFrames--;
        calculateFps(now);
    });
    m_numScheduledFrames++;
}

std::unique_ptr<ITracer> JNIRenderer::makeTracer(bool trace)
{
    if (!trace)
    {
        return std::make_unique<NoopTracer>();
    }

    bool traceAvailable = android_get_device_api_level() >= 23;
    if (traceAvailable)
    {
        return std::make_unique<Tracer>();
    }
    else
    {
        LOGE("JNIRenderer cannot enable tracing on API <23. Api "
             "version is %d",
             android_get_device_api_level());
        return std::make_unique<NoopTracer>();
    }
}

/**
 * Calculate FPS over an average of 10 samples
 */
void JNIRenderer::calculateFps(
    std::chrono::high_resolution_clock::time_point frameTime)
{
    m_tracer->beginSection("calculateFps()");
    static constexpr int FPS_SAMPLES = 10;

    float elapsed =
        std::chrono::duration<float>(frameTime - m_fpsLastFrameTime).count();

    m_fpsSum += 1.0f / elapsed;
    m_fpsCount++;
    if (m_fpsCount == FPS_SAMPLES)
    {
        m_averageFps = m_fpsSum / static_cast<float>(FPS_SAMPLES);
        m_fpsSum = 0;
        m_fpsCount = 0;
    }
    m_fpsLastFrameTime = frameTime;
    m_tracer->endSection();
}
} // namespace rive_android
