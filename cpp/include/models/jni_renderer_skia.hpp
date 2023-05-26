#ifndef _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_

#include <GLES3/gl3.h>
#include <jni.h>

#include "skia_renderer.hpp"

#include "helpers/tracer.hpp"
#include "helpers/egl_share_thread_state.hpp"
#include "helpers/egl_worker.hpp"

namespace rive_android
{
class JNIRendererSkia
{
public:
    JNIRendererSkia(jobject ktObject, bool trace = false);

    ~JNIRendererSkia();

    void setWindow(ANativeWindow* window);

    void doFrame(long frameTimeNs);

    void start(long timeNs);

    void stop();

    WorkerThread<EGLShareThreadState>* worker() const { return mWorker.get(); }

    SkCanvas* canvas() const { return mSkSurface ? mSkSurface->getCanvas() : nullptr; }

    rive::SkiaRenderer* skRenderer() const { return mSkRenderer; }

    float averageFps() const { return mAverageFps; }

    int width() const { return mWindow ? ANativeWindow_getWidth(mWindow) : -1; }

    int height() const { return mWindow ? ANativeWindow_getHeight(mWindow) : -1; }

private:
    void releaseWorkerThreadObjects(EGLShareThreadState*);

    rive::rcp<EGLWorker> mWorker = EGLWorker::Current();

    jobject mKtRenderer;

    ITracer* mTracer;

    // Members that should only be accessed on the worker thread.
    ANativeWindow* mWindow = nullptr;
    EGLSurface mEGLSurface = EGL_NO_SURFACE;
    SkSurface* mSkSurface = nullptr;
    rive::SkiaRenderer* mSkRenderer = nullptr;
    long mLastFrameTimeNs = 0; // TODO: this should be a std::chrono::time_point, or at least 64
                               // bits.

    volatile bool mIsDoingFrame = false;

    /* Helpers for FPS calculations.*/
    std::chrono::steady_clock::time_point mLastFrameTime;
    float mAverageFps = -1.0f;
    float mFpsSum = 0;
    int mFpsCount = 0;

    ITracer* getTracer(bool trace) const;

    void calculateFps();

    void draw(EGLShareThreadState* threadState);
};
} // namespace rive_android
#endif
