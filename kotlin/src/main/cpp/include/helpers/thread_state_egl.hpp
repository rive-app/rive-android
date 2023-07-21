#ifndef _RIVE_ANDROID_EGL_THREAD_STATE_H_
#define _RIVE_ANDROID_EGL_THREAD_STATE_H_

#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

#include "helpers/general.hpp"
#include "helpers/tracer.hpp"

namespace rive_android
{
class EGLThreadState
{
public:
    EGLThreadState();

    virtual ~EGLThreadState() = 0;

    EGLSurface createEGLSurface(ANativeWindow*);

    virtual void destroySurface(EGLSurface) = 0;

    virtual void makeCurrent(EGLSurface) = 0;

    void swapBuffers();

protected:
    virtual void releaseContext() = 0;

    EGLSurface m_currentSurface = EGL_NO_SURFACE;

    EGLDisplay m_display = EGL_NO_DISPLAY;

    EGLContext m_context = EGL_NO_CONTEXT;

private:
    EGLConfig m_config = static_cast<EGLConfig>(0);
};
} // namespace rive_android

#endif
