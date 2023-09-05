#include <thread>
#include <pthread.h>
#include <EGL/egl.h>
#include <android/native_window.h>
#include <GLES3/gl3.h>
#include <jni.h>

#include "models/jni_renderer.hpp"

#include "GrBackendSurface.h"
#include "GrDirectContext.h"
#include "SkCanvas.h"
#include "SkSurface.h"
#include "SkImageInfo.h"
#include "gl/GrGLInterface.h"
#include "gl/GrGLAssembleInterface.h"

using namespace std::chrono_literals;

namespace rive_android
{
JNIRenderer::JNIRenderer(jobject ktObject,
                         bool trace /* = false */,
                         const RendererType rendererType /* = RendererType::Skia */) :
    // Grab a Global Ref to prevent Garbage Collection to clean up the object
    //  from under us since the destructor will be called from the render thread
    //  rather than the UI thread.
    m_worker(EGLWorker::Current(rendererType)),
    m_ktRenderer(GetJNIEnv()->NewGlobalRef(ktObject)),
    m_tracer(getTracer(trace))
{}

JNIRenderer::~JNIRenderer()
{
    // Delete the worker thread objects. And since the worker has captured our "this" pointer,
    // wait for it to finish processing our work before continuing the destruction process.
    m_worker->runAndWait([=](EGLThreadState* threadState) {
        if (!m_workerImpl)
            return;
        m_workerImpl->destroy(threadState);
    });

    // Clean up dependencies.
    auto env = GetJNIEnv();
    jclass ktClass = env->GetObjectClass(m_ktRenderer);
    auto disposeDeps = env->GetMethodID(ktClass, "disposeDependencies", "()V");
    env->CallVoidMethod(m_ktRenderer, disposeDeps);

    env->DeleteGlobalRef(m_ktRenderer);
    if (m_tracer)
    {
        delete m_tracer;
    }

    if (m_window != nullptr)
    {
        ANativeWindow_release(m_window);
    }
}

void JNIRenderer::setWindow(ANativeWindow* window)
{
    if (m_window != nullptr)
    {
        ANativeWindow_release(m_window);
    }
    m_window = window;
    if (m_window != nullptr)
    {
        ANativeWindow_acquire(m_window);
    }
    m_worker->run([=](EGLThreadState* threadState) {
        m_workerThreadID = std::this_thread::get_id();
        if (m_workerImpl)
        {
            m_workerImpl->destroy(threadState);
            m_workerImpl.reset();
        }
        if (m_window)
        {
            m_workerImpl = WorkerImpl::Make(m_window, threadState, m_worker->rendererType());
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
    m_worker->run([=](EGLThreadState* threadState) {
        if (!m_workerImpl)
            return;
        auto now = std::chrono::steady_clock::now();
        m_fpsLastFrameTime = now;
        m_workerImpl->start(m_ktRenderer, now);
    });
}

void JNIRenderer::stop()
{
    m_worker->run([=](EGLThreadState* threadState) {
        if (!m_workerImpl)
            return;
        m_workerImpl->stop();
    });
}

void JNIRenderer::doFrame()
{
    if (m_numScheduledFrames >= kMaxScheduledFrames)
    {
        return;
    }

    m_worker->run([=](EGLThreadState* threadState) {
        if (!m_workerImpl)
            return;
        auto now = std::chrono::high_resolution_clock::now();
        m_workerImpl->doFrame(m_tracer, threadState, m_ktRenderer, now);
        m_numScheduledFrames--;
        calculateFps(now);
    });
    m_numScheduledFrames++;
}

ITracer* JNIRenderer::getTracer(bool trace) const
{
    if (!trace)
    {
        return new NoopTracer();
    }

    bool traceAvailable = android_get_device_api_level() >= 23;
    if (traceAvailable)
    {
        return new Tracer();
    }
    else
    {
        LOGE("JNIRenderer cannot enable tracing on API <23. Api "
             "version is %d",
             android_get_device_api_level());
        return new NoopTracer();
    }
}

/**
 * Calculate FPS over an average of 10 samples
 */
void JNIRenderer::calculateFps(std::chrono::high_resolution_clock::time_point frameTime)
{
    m_tracer->beginSection("calculateFps()");
    static constexpr int FPS_SAMPLES = 10;

    float elapsed = std::chrono::duration<float>(frameTime - m_fpsLastFrameTime).count();

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
