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
    mWorkerThread(ThreadManager::getInstance()->acquireThread("EGLRenderer")),
    mKtRenderer(getJNIEnv()->NewWeakGlobalRef(ktObject)),
    mTracer(getTracer(trace)),
    mWindow(nullptr),
    mGpuCanvas(nullptr),
    mSkRenderer(nullptr)
{}

JNIRendererSkia::~JNIRendererSkia()
{
    // Clean up dependencies.
    auto env = getJNIEnv();
    jclass ktClass = env->GetObjectClass(mKtRenderer);
    auto disposeDeps = env->GetMethodID(ktClass, "disposeDependencies", "()V");
    env->CallVoidMethod(mKtRenderer, disposeDeps);

    // N.B. Make sure the thread is removed before the Global Ref.
    env->DeleteWeakGlobalRef(mKtRenderer);
    if (mSkRenderer)
    {
        delete mSkRenderer;
    }
    if (mTracer)
    {
        delete mTracer;
    }
    if (mWindow)
    {
        ANativeWindow_release(mWindow);
    }
}

void JNIRendererSkia::setWindow(ANativeWindow* window)
{
    mWorkerThread->run([=](EGLThreadState* threadState) {
        if (!threadState->setWindow(window))
        {
            return;
        }

        ANativeWindow_acquire(window);
        mWindow = window;

        auto gpuSurface = threadState->getSkiaSurface();
        mGpuCanvas = gpuSurface->getCanvas();
        mSkRenderer = new rive::SkiaRenderer(mGpuCanvas);
    });
}

void JNIRendererSkia::doFrame(long frameTimeNs)
{
    if (mIsDoingFrame)
    {
        return;
    }
    mIsDoingFrame = true;
    bool hasQueued = mWorkerThread->run([=](EGLThreadState* threadState) {
        float elapsedMs = threadState->getElapsedMs(frameTimeNs);
        threadState->mLastUpdate = frameTimeNs;

        auto env = getJNIEnv();
        env->CallVoidMethod(mKtRenderer, threadState->mKtAdvanceCallback, elapsedMs);
        draw(threadState);
        mIsDoingFrame = false;
    });
    if (!hasQueued)
    {
        mIsDoingFrame = false;
    }
}

void JNIRendererSkia::start(long timeNs)
{
    mWorkerThread->run([=](EGLThreadState* threadState) {
        threadState->mIsStarted = true;

        jclass ktClass = getJNIEnv()->GetObjectClass(mKtRenderer);
        threadState->setKtRendererClass(ktClass);

        threadState->mLastUpdate = timeNs;
        mLastFrameTime = std::chrono::steady_clock::now();
    });
}

void JNIRendererSkia::stop()
{
    // Stop immediately
    mWorkerThread->drainWorkQueue();
    mWorkerThread->run([=](EGLThreadState* threadState) { threadState->mIsStarted = false; });
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

void JNIRendererSkia::draw(EGLThreadState* threadState)
{

    // Don't render if we have no surface
    if (threadState->hasNoSurface())
    {
        LOGE("Has No Surface!");
        // Sleep a bit so we don't churn too fast
        std::this_thread::sleep_for(50ms);
        return;
    }

    calculateFps();

    mTracer->beginSection("draw()");
    mGpuCanvas->drawColor(SK_ColorTRANSPARENT, SkBlendMode::kClear);
    auto env = getJNIEnv();
    // Kotlin callback.
    env->CallVoidMethod(mKtRenderer, threadState->mKtDrawCallback);

    mTracer->beginSection("flush()");
    threadState->flush();
    mTracer->endSection(); // flush

    mTracer->beginSection("swapBuffers()");
    threadState->swapBuffers();
    mTracer->endSection(); // swapBuffers

    mTracer->endSection(); // draw()
}

} // namespace rive_android
