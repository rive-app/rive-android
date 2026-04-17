#pragma once

#include "helpers/general.hpp"
#include "helpers/tracer.hpp"
#include "models/egl_result.hpp"

#include <android/native_window.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <jni.h>

namespace rive_android
{
class DrawableThreadState
{
public:
    virtual ~DrawableThreadState() = default;
    virtual EGLResult swapBuffers() = 0;
    virtual EGLResult recoverAfterContextLoss() { return EGLResult::Ok(); }
};

class EGLThreadState : public DrawableThreadState
{
public:
    EGLThreadState();

    ~EGLThreadState() override = 0;

    EGLSurface createEGLSurface(ANativeWindow*);
    virtual void destroySurface(EGLSurface) = 0;

    virtual EGLResult makeCurrent(EGLSurface) = 0;
    EGLResult swapBuffers() override;

    /**
     * Rebuilds EGL display/config/context state after a fatal EGL failure.
     *
     * Call this from the worker thread when rendering has detected
     * context-integrity loss (e.g. failed makeCurrent/swap with fatal error)
     * and normal frame execution should be replaced by recovery attempts.
     *
     * @return EGLResult::Ok() when EGL state was reinitialized successfully;
     * otherwise an EGLResult failure describing the recovery error.
     */
    EGLResult recoverAfterContextLoss() override;

protected:
    EGLResult initializeEGLState();
    void teardownEGLState();

    bool hasValidContext() const
    {
        return m_display != EGL_NO_DISPLAY && m_context != EGL_NO_CONTEXT;
    }

    EGLSurface m_currentSurface = EGL_NO_SURFACE;
    EGLDisplay m_display = EGL_NO_DISPLAY;
    EGLContext m_context = EGL_NO_CONTEXT;
    EGLConfig m_config = static_cast<EGLConfig>(nullptr);
};

class CanvasThreadState : public DrawableThreadState
{
public:
    EGLResult swapBuffers() override { return EGLResult::Ok(); }
};
} // namespace rive_android
