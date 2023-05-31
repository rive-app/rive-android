#include <thread>
#include <pthread.h>
#include <EGL/egl.h>
#include <android/native_window.h>
#include <GLES3/gl3.h>
#include <jni.h>

#include "models/jni_renderer_skia.hpp"

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
// Class that executes on JNIRendererSkia's worker thread.
class JNIRendererSkia::WorkerSideImpl
{
public:
    static std::unique_ptr<WorkerSideImpl> Make(ANativeWindow* window,
                                                EGLShareThreadState* threadState)
    {
        bool success;
        std::unique_ptr<WorkerSideImpl> impl(new WorkerSideImpl(window, threadState, &success));
        if (!success)
        {
            impl->destroy(threadState);
            impl.reset();
        }
        return impl;
    }

    ~WorkerSideImpl()
    {
        // Call destroy() fist!
        assert(!m_isStarted);
        assert(m_eglSurface == EGL_NO_SURFACE);
    }

    void destroy(EGLShareThreadState* threadState)
    {
        m_skRenderer.reset();
        m_skSurface.reset();
        if (m_eglSurface != EGL_NO_SURFACE)
        {
            threadState->destroySurface(m_eglSurface);
            m_eglSurface = EGL_NO_SURFACE;
        }
    }

    rive::SkiaRenderer* skRenderer() const { return m_skRenderer.get(); }

    void start(jobject ktRenderer, long timeNs)
    {
        auto env = getJNIEnv();
        jclass ktClass = getJNIEnv()->GetObjectClass(ktRenderer);
        m_ktRendererClass = reinterpret_cast<jclass>(env->NewWeakGlobalRef(ktClass));
        m_ktDrawCallback = env->GetMethodID(m_ktRendererClass, "draw", "()V");
        m_ktAdvanceCallback = env->GetMethodID(m_ktRendererClass, "advance", "(F)V");
        mLastFrameTimeNs = timeNs;
        m_isStarted = true;
    }

    void stop()
    {
        auto env = getJNIEnv();
        if (m_ktRendererClass != nullptr)
        {
            env->DeleteWeakGlobalRef(m_ktRendererClass);
        }
        m_ktRendererClass = nullptr;
        m_ktDrawCallback = nullptr;
        m_ktAdvanceCallback = nullptr;
        m_isStarted = false;
    }

    void doFrame(ITracer* tracer,
                 EGLShareThreadState* threadState,
                 jobject ktRenderer,
                 long frameTimeNs)
    {
        if (!m_isStarted)
        {
            return;
        }

        float elapsedMs = (frameTimeNs - mLastFrameTimeNs) * 1e-9f;
        mLastFrameTimeNs = frameTimeNs;

        auto env = getJNIEnv();
        env->CallVoidMethod(ktRenderer, m_ktAdvanceCallback, elapsedMs);

        tracer->beginSection("draw()");

        // Bind context to this thread.
        threadState->makeCurrent(m_eglSurface);
        m_skSurface->getCanvas()->clear(SkColor((0x00000000)));
        // Kotlin callback.
        env->CallVoidMethod(ktRenderer, m_ktDrawCallback);

        tracer->beginSection("flush()");
        m_skSurface->flushAndSubmit();
        tracer->endSection(); // flush

        tracer->beginSection("swapBuffers()");
        threadState->swapBuffers();

        tracer->endSection(); // swapBuffers
        tracer->endSection(); // draw()
    }

private:
    WorkerSideImpl(ANativeWindow* window, EGLShareThreadState* threadState, bool* success)
    {
        *success = false;
        m_eglSurface = threadState->createEGLSurface(window);
        if (m_eglSurface == EGL_NO_SURFACE)
            return;
        m_skSurface = threadState->createSkiaSurface(m_eglSurface,
                                                     ANativeWindow_getWidth(window),
                                                     ANativeWindow_getHeight(window));
        if (m_skSurface == nullptr)
            return;
        m_skRenderer = std::make_unique<rive::SkiaRenderer>(m_skSurface->getCanvas());
        *success = true;
    }

    EGLSurface m_eglSurface = EGL_NO_SURFACE;
    sk_sp<SkSurface> m_skSurface;
    std::unique_ptr<rive::SkiaRenderer> m_skRenderer;

    jclass m_ktRendererClass = nullptr;
    jmethodID m_ktDrawCallback = nullptr;
    jmethodID m_ktAdvanceCallback = nullptr;
    long mLastFrameTimeNs = 0; // TODO: this should be a std::chrono::time_point, or at least 64
                               // bits.
    bool m_isStarted = false;
};

JNIRendererSkia::JNIRendererSkia(jobject ktObject, bool trace) :
    // Grab a Global Ref to prevent Garbage Collection to clean up the object
    //  from under us since the destructor will be called from the render thread
    //  rather than the UI thread.
    m_ktRenderer(getJNIEnv()->NewGlobalRef(ktObject)),
    m_tracer(getTracer(trace))
{}

JNIRendererSkia::~JNIRendererSkia()
{
    // Delete the worker thread objects. And since the worker has captured our "this" pointer,
    // wait for it to finish processing our work before continuing the destruction process.
    mWorker->runAndWait([=](EGLShareThreadState* threadState) {
        if (!m_workerSideImpl)
            return;
        m_workerSideImpl->destroy(threadState);
    });

    // Clean up dependencies.
    auto env = getJNIEnv();
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

void JNIRendererSkia::setWindow(ANativeWindow* window)
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
    mWorker->run([=](EGLShareThreadState* threadState) {
        m_workerThreadID = std::this_thread::get_id();
        if (m_workerSideImpl)
        {
            m_workerSideImpl->destroy(threadState);
            m_workerSideImpl.reset();
        }
        if (m_window)
        {
            m_workerSideImpl = WorkerSideImpl::Make(m_window, threadState);
        }
    });
}

rive::Renderer* JNIRendererSkia::getRendererOnWorkerThread() const
{
    assert(std::this_thread::get_id() == m_workerThreadID);
    if (std::this_thread::get_id() != m_workerThreadID)
        return nullptr;
    if (!m_workerSideImpl)
        return nullptr;
    return m_workerSideImpl->skRenderer();
}

void JNIRendererSkia::start(long timeNs)
{
    mWorker->run([=](EGLShareThreadState* threadState) {
        if (!m_workerSideImpl)
            return;
        m_workerSideImpl->start(m_ktRenderer, timeNs);
    });
    mLastFrameTime = std::chrono::steady_clock::now();
}

void JNIRendererSkia::stop()
{
    // TODO: There is something wrong here when onVisibilityChanged() is called.
    // Stop immediately
    mWorker->run([=](EGLShareThreadState* threadState) {
        if (!m_workerSideImpl)
            return;
        m_workerSideImpl->stop();
    });
}

void JNIRendererSkia::doFrame(long frameTimeNs)
{
    mWorker->waitUntilComplete(m_workIDForLastFrame);
    m_workIDForLastFrame = mWorker->run([=](EGLShareThreadState* threadState) {
        if (!m_workerSideImpl)
            return;
        m_workerSideImpl->doFrame(m_tracer, threadState, m_ktRenderer, frameTimeNs);
    });
    calculateFps();
}

ITracer* JNIRendererSkia::getTracer(bool trace) const
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
        LOGE("JNIRendererSkia cannot enable tracing on API <23. Api "
             "version is %d",
             android_get_device_api_level());
        return new NoopTracer();
    }
}

/**
 * Calculate FPS over an average of 10 samples
 */
void JNIRendererSkia::calculateFps()
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
