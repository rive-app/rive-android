#ifndef _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_

#include <GLES3/gl3.h>
#include <jni.h>

#include "skia_renderer.hpp"

#include "helpers/tracer.hpp"
#include "helpers/egl_thread_state.hpp"
#include "helpers/worker_thread.hpp"

namespace rive_android {
    class JNIRendererSkia {
    public:
        JNIRendererSkia(jobject ktObject, bool trace = false);

        ~JNIRendererSkia();

        void setWindow(ANativeWindow* window);

        void doFrame(long frameTimeNs);

        void start();

        void stop();

        WorkerThread<EGLThreadState>* workerThread() const { return mWorkerThread; }

        SkCanvas* canvas() const { return mGpuCanvas; }

        rive::SkiaRenderer* skRenderer() const { return mSkRenderer; }

        float averageFps() const { return mAverageFps; }

        int width() const { return mWindow ? ANativeWindow_getWidth(mWindow) : -1; }

        int height() const { return mWindow ? ANativeWindow_getHeight(mWindow) : -1; }

    private:
        WorkerThread<EGLThreadState>* mWorkerThread;

        jobject mKtRenderer;

        ITracer* mTracer;

        ANativeWindow* mWindow;

        SkCanvas* mGpuCanvas;

        rive::SkiaRenderer* mSkRenderer;

        bool mIsDoingFrame = false;

        /* Helpers for FPS calculations.*/
        std::chrono::steady_clock::time_point mLastFrameTime;
        float mAverageFps = -1.0f;
        float mFpsSum = 0;
        int mFpsCount = 0;

        ITracer* getTracer(bool trace) const;

        void calculateFps();

        void draw(EGLThreadState* threadState);
    };
} // namespace rive_android
#endif