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
JNIRendererSkia::JNIRendererSkia(jobject ktObject, bool trace) :
    // Grab a Global Ref to prevent Garbage Collection to clean up the object
    //  from under us since the destructor will be called from the render thread
    //  rather than the UI thread.
    mKtRenderer(getJNIEnv()->NewGlobalRef(ktObject)),
    mTracer(getTracer(trace))
{}

JNIRendererSkia::~JNIRendererSkia()
{
    // Delete the worker thread objects. And since the worker has captured our "this" pointer,
    // wait for it to finish processing our work before continuing the destruction process.
    {
        std::mutex completedMutex;
        std::condition_variable_any completedCondition;
        std::unique_lock lock(completedMutex);
        mWorker->run([=, &completedMutex, &completedCondition](EGLShareThreadState* threadState) {
            {
                std::unique_lock workerLock(completedMutex);
                releaseWorkerThreadObjects(threadState);
            }
            completedCondition.notify_one();
        });
        completedCondition.wait(completedMutex);
    }

    // Clean up dependencies.
    auto env = getJNIEnv();
    jclass ktClass = env->GetObjectClass(mKtRenderer);
    auto disposeDeps = env->GetMethodID(ktClass, "disposeDependencies", "()V");
    env->CallVoidMethod(mKtRenderer, disposeDeps);

    env->DeleteGlobalRef(mKtRenderer);
    if (mTracer)
    {
        delete mTracer;
    }
}

void JNIRendererSkia::releaseWorkerThreadObjects(EGLShareThreadState* threadState)
{
    if (mSkRenderer != nullptr)
    {
        delete mSkRenderer;
        mSkRenderer = nullptr;
    }
    if (mSkSurface != nullptr)
    {
        mSkSurface->unref();
        mSkSurface = nullptr;
    }
    if (mEGLSurface != EGL_NO_SURFACE)
    {
        threadState->destroySurface(mEGLSurface);
        mEGLSurface = EGL_NO_SURFACE;
    }
    if (mWindow != nullptr)
    {
        ANativeWindow_release(mWindow);
        mWindow = nullptr;
    }
}

void JNIRendererSkia::setWindow(ANativeWindow* window)
{
    mWorker->run([=](EGLShareThreadState* threadState) {
        releaseWorkerThreadObjects(threadState);
        mWindow = window;
        if (mWindow == nullptr)
            return;
        ANativeWindow_acquire(mWindow);
        mEGLSurface = threadState->createEGLSurface(mWindow);
        if (mEGLSurface == EGL_NO_SURFACE)
            return;
        mSkSurface = threadState->createSkiaSurface(mEGLSurface, width(), height()).release();
        if (mSkSurface == nullptr)
            return;
        mSkRenderer = new rive::SkiaRenderer(mSkSurface->getCanvas());
    });
}

void JNIRendererSkia::doFrame(long frameTimeNs)
{
    if (mIsDoingFrame)
    {
        return;
    }
    mIsDoingFrame = true;
    bool hasQueued = mWorker->run([=](EGLShareThreadState* threadState) {
        float elapsedMs = (frameTimeNs - mLastFrameTimeNs) * 1e-9f;
        mLastFrameTimeNs = frameTimeNs;

        auto env = getJNIEnv();
        env->CallVoidMethod(mKtRenderer, threadState->mKtAdvanceCallback, elapsedMs);
        calculateFps();
        threadState->doDraw(mTracer, mEGLSurface, mSkSurface, mKtRenderer);
        mIsDoingFrame = false;
    });
    if (!hasQueued)
    {
        mIsDoingFrame = false;
    }
}

void JNIRendererSkia::start(long timeNs)
{
    mWorker->run([=](EGLShareThreadState* threadState) {
        threadState->mIsStarted = true;

        jclass ktClass = getJNIEnv()->GetObjectClass(mKtRenderer);
        threadState->setKtRendererClass(ktClass);

        mLastFrameTimeNs = timeNs;
        mLastFrameTime = std::chrono::steady_clock::now();
    });
}

void JNIRendererSkia::stop()
{
    // TODO: There is something wrong here when onVisibilityChanged() is called.
    // Stop immediately
    mWorker->run([=](EGLShareThreadState* threadState) { threadState->mIsStarted = false; });
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
    mTracer->beginSection("calculateFps()");
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
    mTracer->endSection();
}
} // namespace rive_android
