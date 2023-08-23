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
    mWorker(EGLWorker::Current(rendererType)),
    m_ktRenderer(GetJNIEnv()->NewGlobalRef(ktObject)),
    m_tracer(getTracer(trace))
{}

JNIRenderer::~JNIRenderer()
{
    // Delete the worker thread objects. And since the worker has captured our "this" pointer,
    // wait for it to finish processing our work before continuing the destruction process.
    mWorker->runAndWait([=](EGLThreadState* threadState) {
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
    mWorker->run([=](EGLThreadState* threadState) {
        m_workerThreadID = std::this_thread::get_id();
        if (m_workerImpl)
        {
            m_workerImpl->destroy(threadState);
            m_workerImpl.reset();
        }
        if (m_window)
        {
            m_workerImpl = WorkerImpl::Make(m_window, threadState, mWorker->rendererType());
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

void JNIRenderer::start(long long timeNs)
{
    mWorker->run([=](EGLThreadState* threadState) {
        if (!m_workerImpl)
            return;
        m_workerImpl->start(m_ktRenderer, timeNs);
    });
    mLastFrameTime = std::chrono::steady_clock::now();
}

void JNIRenderer::stop()
{
    mWorker->run([=](EGLThreadState* threadState) {
        if (!m_workerImpl)
            return;
        m_workerImpl->stop();
    });
}

void JNIRenderer::doFrame(long long frameTimeNs)
{
    if (!mWorker->canScheduleWork(m_workIDForLastFrame))
    {
        return;
    }
    m_workIDForLastFrame = mWorker->run([=](EGLThreadState* threadState) {
        if (!m_workerImpl)
            return;
        m_workerImpl->doFrame(m_tracer, threadState, m_ktRenderer, frameTimeNs);
    });
    calculateFps();
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
void JNIRenderer::calculateFps()
{
    m_tracer->beginSection("calculateFps()");
    static constexpr int FPS_SAMPLES = 10;

    std::chrono::steady_clock::time_point now = std::chrono::steady_clock::now();

    mFpsSum += 1.0f / ((now - mLastFrameTime).count() / 1e9f);
    mFpsCount++;
    if (mFpsCount == FPS_SAMPLES)
    {
        mAverageFps = mFpsSum / mFpsCount;
        mFpsSum = 0;
        mFpsCount = 0;
    }
    mLastFrameTime = now;
    m_tracer->endSection();
}
} // namespace rive_android
