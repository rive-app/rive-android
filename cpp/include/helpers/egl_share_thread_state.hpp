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

    EGLSurface createEGLSurface(ANativeWindow*);
    sk_sp<SkSurface> createSkiaSurface(EGLSurface, int width, int height);

    void destroySurface(EGLSurface);

    void doDraw(ITracer*, EGLSurface, SkSurface*, jobject ktRenderer) const;

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

    bool mIsStarted = false;
    jmethodID mKtDrawCallback = nullptr;
    jmethodID mKtAdvanceCallback = nullptr;

private:
    SkiaContextManager mSkiaContextManager;

    jclass mKtRendererClass = nullptr;

    void swapBuffers(EGLSurface eglSurface) const;
};
} // namespace rive_android

#endif
