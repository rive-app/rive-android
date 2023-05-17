#ifndef _RIVE_ANDROID_EGL_THREAD_STATE_H_
#define _RIVE_ANDROID_EGL_THREAD_STATE_H_

#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

#include "helpers/general.hpp"
#include "helpers/skia_context.hpp"
#include "helpers/tracer.hpp"

#include "GrDirectContext.h"
#include "SkSurface.h"
#include "SkCanvas.h"

namespace rive_android
{
class EGLShareThreadState
{
public:
    EGLShareThreadState();
    ~EGLShareThreadState();

    bool setWindow(ANativeWindow*);
    void clearSurface();
    void doDraw(ITracer* tracer, SkCanvas* canvas, jobject ktRenderer) const;

    void unsetKtRendererClass()
    {
        auto env = getJNIEnv();
        if (mKtRendererClass != nullptr)
        {
            env->DeleteWeakGlobalRef(mKtRendererClass);
        }
        mKtRendererClass = nullptr;
        mKtDrawCallback = nullptr;
        mKtAdvanceCallback = nullptr;
    }

    void setKtRendererClass(jclass localReference)
    {
        auto env = getJNIEnv();
        mKtRendererClass = reinterpret_cast<jclass>(env->NewWeakGlobalRef(localReference));
        mKtDrawCallback = env->GetMethodID(mKtRendererClass, "draw", "()V");
        mKtAdvanceCallback = env->GetMethodID(mKtRendererClass, "advance", "(F)V");
    }

    static long getNowNs()
    {
        using namespace std::chrono;
        // Reset time to avoid super-large update of position
        auto nowNs = time_point_cast<nanoseconds>(steady_clock::now());
        return nowNs.time_since_epoch().count();
    }

    float getElapsedMs(long frameTimeNs) const
    {
        float elapsedMs = (frameTimeNs - mLastUpdate) / 1e9f;
        return elapsedMs;
    }

    bool mIsStarted = false;
    jmethodID mKtDrawCallback = nullptr;
    jmethodID mKtAdvanceCallback = nullptr;

    // Last update time in nanoseconds
    long mLastUpdate = 0;

    sk_sp<SkSurface> getSkiaSurface() const { return mSkSurface; }

private:
    std::shared_ptr<SkiaContextManager> mSkiaContextManager;
    EGLSurface mSurface = EGL_NO_SURFACE;
    sk_sp<SkSurface> mSkSurface = nullptr;

    jclass mKtRendererClass = nullptr;

    bool hasNoSurface() const { return mSurface == EGL_NO_SURFACE || mSkSurface == nullptr; }
    void swapBuffers() const;
    void flush() const;
};
} // namespace rive_android

#endif