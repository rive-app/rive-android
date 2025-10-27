#pragma once

#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

#include "helpers/general.hpp"
#include "helpers/tracer.hpp"

namespace rive_android
{
class DrawableThreadState
{
public:
    virtual ~DrawableThreadState() = default;
    virtual void swapBuffers() = 0;
};

class EGLThreadState : public DrawableThreadState
{
public:
    EGLThreadState();

    ~EGLThreadState() override = 0;

    EGLSurface createEGLSurface(ANativeWindow*);

    virtual void destroySurface(EGLSurface) = 0;

    virtual void makeCurrent(EGLSurface) = 0;

    void swapBuffers() override;

protected:
    EGLSurface m_currentSurface = EGL_NO_SURFACE;
    EGLDisplay m_display = EGL_NO_DISPLAY;
    EGLContext m_context = EGL_NO_CONTEXT;
    EGLConfig m_config = static_cast<EGLConfig>(nullptr);
};

class CanvasThreadState : public DrawableThreadState
{
public:
    void swapBuffers() override {}
};
} // namespace rive_android
