#ifndef _RIVE_ANDROID_EGL_THREAD_STATE_H_
#define _RIVE_ANDROID_EGL_THREAD_STATE_H_

#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

#include "helpers/general.hpp"
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

    void makeCurrent(EGLSurface);
    void swapBuffers();

private:
    EGLDisplay m_display = EGL_NO_DISPLAY;
    EGLConfig m_config = static_cast<EGLConfig>(0);
    EGLContext m_context = EGL_NO_CONTEXT;
    sk_sp<GrDirectContext> m_skContext = nullptr;
    EGLSurface m_currentSurface = EGL_NO_SURFACE;
};
} // namespace rive_android

#endif
