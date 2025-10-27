#include <thread>
#include <vector>

#include "helpers/thread_state_egl.hpp"

namespace rive_android
{
static bool config_has_attribute(EGLDisplay display,
                                 EGLConfig config,
                                 EGLint attribute,
                                 EGLint value)
{
    EGLint outValue = 0;
    EGLBoolean result =
        eglGetConfigAttrib(display, config, attribute, &outValue);
    EGL_ERR_CHECK();
    return result && (outValue == value);
}

EGLThreadState::EGLThreadState()
{
    m_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (m_display == EGL_NO_DISPLAY)
    {
        EGL_ERR_CHECK();
        LOGE("eglGetDisplay() failed.");
        return;
    }

    if (!eglInitialize(m_display, nullptr, nullptr))
    {
        EGL_ERR_CHECK();
        LOGE("eglInitialize() failed.");
        return;
    }

    const EGLint configAttributes[] = {EGL_RENDERABLE_TYPE,
                                       EGL_OPENGL_ES2_BIT,
                                       EGL_BLUE_SIZE,
                                       8,
                                       EGL_GREEN_SIZE,
                                       8,
                                       EGL_RED_SIZE,
                                       8,
                                       EGL_DEPTH_SIZE,
                                       0,
                                       EGL_STENCIL_SIZE,
                                       8,
                                       EGL_ALPHA_SIZE,
                                       8,
                                       EGL_NONE};

    EGLint num_configs = 0;
    if (!eglChooseConfig(m_display, configAttributes, nullptr, 0, &num_configs))
    {
        EGL_ERR_CHECK();
        LOGE("eglChooseConfig() didn't find any (%d)", num_configs);
        return;
    }

    std::vector<EGLConfig> supportedConfigs(static_cast<size_t>(num_configs));
    eglChooseConfig(m_display,
                    configAttributes,
                    supportedConfigs.data(),
                    num_configs,
                    &num_configs);
    EGL_ERR_CHECK();

    // Choose a config, either a match if possible or the first config otherwise
    const auto configMatches = [&](EGLConfig config) {
        if (!config_has_attribute(m_display, m_config, EGL_RED_SIZE, 8))
            return false;
        if (!config_has_attribute(m_display, m_config, EGL_GREEN_SIZE, 8))
            return false;
        if (!config_has_attribute(m_display, m_config, EGL_BLUE_SIZE, 8))
            return false;
        if (!config_has_attribute(m_display, m_config, EGL_STENCIL_SIZE, 8))
            return false;
        return config_has_attribute(m_display, m_config, EGL_DEPTH_SIZE, 0);
    };

    const auto configIter = std::find_if(supportedConfigs.cbegin(),
                                         supportedConfigs.cend(),
                                         configMatches);

    m_config = (configIter != supportedConfigs.cend()) ? *configIter
                                                       : supportedConfigs[0];

    const EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION,
                                        2,
                                        EGL_NONE};

    m_context =
        eglCreateContext(m_display, m_config, nullptr, contextAttributes);
    if (m_context == EGL_NO_CONTEXT)
    {
        LOGE("eglCreateContext() failed.");
        EGL_ERR_CHECK();
    }
}

EGLThreadState::~EGLThreadState()
{
    LOGD("EGLThreadState getting destroyed! 🧨");

    if (m_context != EGL_NO_CONTEXT)
    {
        eglDestroyContext(m_display, m_context);
        EGL_ERR_CHECK();
    }

    eglReleaseThread();
    EGL_ERR_CHECK();

    if (m_display != EGL_NO_DISPLAY)
    {
        eglTerminate(m_display);
        EGL_ERR_CHECK();
    }
}

EGLSurface EGLThreadState::createEGLSurface(ANativeWindow* window)
{
    if (!window)
    {
        return EGL_NO_SURFACE;
    }

    LOGD("eglCreateWindowSurface()");
    auto res = eglCreateWindowSurface(m_display, m_config, window, nullptr);
    EGL_ERR_CHECK();
    return res;
}

void EGLThreadState::swapBuffers()
{
    eglSwapBuffers(m_display, m_currentSurface);
    EGL_ERR_CHECK();
}
} // namespace rive_android
