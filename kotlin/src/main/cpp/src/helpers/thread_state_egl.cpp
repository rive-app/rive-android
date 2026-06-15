#include "helpers/thread_state_egl.hpp"

#include <algorithm>
#include <thread>
#include <vector>

#include "helpers/egl_error.hpp"
#include "helpers/rive_log.hpp"

namespace rive_android
{
constexpr auto* TAG = "RiveLN/EGLThreadState";

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
    RiveLogD(TAG, "Creating EGLThreadState.");
    const EGLResult initResult = initializeEGLState();
    if (!initResult.isSuccess())
    {
        RiveLogE(TAG,
                 "Failed to initialize EGLThreadState: %s",
                 initResult.summary().c_str());
    }
}

EGLThreadState::~EGLThreadState()
{
    RiveLogD(TAG, "Deleting EGLThreadState! 🧨");
    teardownEGLState();
}

EGLResult EGLThreadState::initializeEGLState()
{
    RiveLogD(TAG, "Initializing display.");
    m_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (m_display == EGL_NO_DISPLAY)
    {
        EGLint error = consume_egl_error_or_default(EGL_BAD_DISPLAY, TAG);
        RiveLogE(TAG,
                 "eglGetDisplay() failed: %s",
                 EGLErrorString(error).c_str());
        return EGLResult::Failure(EGLResult::FailureOperation::recover, error);
    }

    if (!eglInitialize(m_display, nullptr, nullptr))
    {
        EGLint error = consume_egl_error_or_default(EGL_NOT_INITIALIZED, TAG);
        RiveLogE(TAG,
                 "eglInitialize() failed: %s",
                 EGLErrorString(error).c_str());
        return EGLResult::Failure(EGLResult::FailureOperation::recover, error);
    }

    RiveLogD(TAG, "Initializing EGL config.");
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
        EGLint error = consume_egl_error_or_default(EGL_BAD_CONFIG, TAG);
        RiveLogE(TAG,
                 "eglChooseConfig() failed: %s",
                 EGLErrorString(error).c_str());
        return EGLResult::Failure(EGLResult::FailureOperation::recover, error);
    }
    if (num_configs <= 0)
    {
        constexpr EGLint error = EGL_BAD_CONFIG;
        RiveLogE(
            TAG,
            "eglChooseConfig() didn't find any suitable configurations: %s",
            EGLErrorString(error).c_str());
        return EGLResult::Failure(EGLResult::FailureOperation::recover, error);
    }

    std::vector<EGLConfig> supportedConfigs(static_cast<size_t>(num_configs));
    if (!eglChooseConfig(m_display,
                         configAttributes,
                         supportedConfigs.data(),
                         num_configs,
                         &num_configs))
    {
        EGLint error = consume_egl_error_or_default(EGL_BAD_CONFIG, TAG);
        RiveLogE(TAG,
                 "eglChooseConfig() failed when fetching configs: %s",
                 EGLErrorString(error).c_str());
        return EGLResult::Failure(EGLResult::FailureOperation::recover, error);
    }
    if (num_configs <= 0)
    {
        constexpr EGLint error = EGL_BAD_CONFIG;
        RiveLogE(TAG,
                 "No EGL configs were returned: %s",
                 EGLErrorString(error).c_str());
        return EGLResult::Failure(EGLResult::FailureOperation::recover, error);
    }

    // Choose a config, either a match if possible or the first config
    // otherwise.
    const auto configMatches = [&](EGLConfig config) {
        if (!config_has_attribute(m_display, config, EGL_RED_SIZE, 8))
            return false;
        if (!config_has_attribute(m_display, config, EGL_GREEN_SIZE, 8))
            return false;
        if (!config_has_attribute(m_display, config, EGL_BLUE_SIZE, 8))
            return false;
        if (!config_has_attribute(m_display, config, EGL_STENCIL_SIZE, 8))
            return false;
        return config_has_attribute(m_display, config, EGL_DEPTH_SIZE, 0);
    };

    const auto configIter = std::find_if(supportedConfigs.cbegin(),
                                         supportedConfigs.cend(),
                                         configMatches);

    m_config = (configIter != supportedConfigs.cend()) ? *configIter
                                                       : supportedConfigs[0];

    const EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION,
                                        2,
                                        EGL_NONE};

    RiveLogD(TAG, "Creating EGL context.");
    m_context =
        eglCreateContext(m_display, m_config, nullptr, contextAttributes);
    if (m_context == EGL_NO_CONTEXT)
    {
        EGLint error = consume_egl_error_or_default(EGL_BAD_CONTEXT, TAG);
        RiveLogE(TAG,
                 "eglCreateContext() failed: %s",
                 EGLErrorString(error).c_str());
        return EGLResult::Failure(EGLResult::FailureOperation::recover, error);
    }

    return EGLResult::Ok();
}

void EGLThreadState::teardownEGLState()
{
    if (m_context != EGL_NO_CONTEXT && m_display != EGL_NO_DISPLAY)
    {
        RiveLogD(TAG, "Destroying context.");
        if (!eglDestroyContext(m_display, m_context))
        {
            EGLint error = eglGetError();
            RiveLogW(TAG,
                     "eglDestroyContext() failed during teardown: %s",
                     EGLErrorString(error).c_str());
        }
    }
    m_context = EGL_NO_CONTEXT;
    m_currentSurface = EGL_NO_SURFACE;
    m_config = static_cast<EGLConfig>(nullptr);

    RiveLogD(TAG, "Releasing thread.");
    if (!eglReleaseThread())
    {
        EGLint error = eglGetError();
        RiveLogW(TAG,
                 "eglReleaseThread() failed during teardown: %s",
                 EGLErrorString(error).c_str());
    }

    if (m_display != EGL_NO_DISPLAY)
    {
        RiveLogD(TAG, "Terminating display.");
        if (!eglTerminate(m_display))
        {
            EGLint error = eglGetError();
            RiveLogW(TAG,
                     "eglTerminate() failed during teardown: %s",
                     EGLErrorString(error).c_str());
        }
    }
    m_display = EGL_NO_DISPLAY;
}

EGLSurface EGLThreadState::createEGLSurface(ANativeWindow* window)
{
    if (!window)
    {
        RiveLogD(TAG, "Window is null - returning EGL_NO_SURFACE.");
        return EGL_NO_SURFACE;
    }
    if (!hasValidContext())
    {
        RiveLogW(TAG,
                 "Cannot create EGL surface: display/context are not valid.");
        return EGL_NO_SURFACE;
    }

    RiveLogD(TAG, "Creating EGL surface.");
    auto surface = eglCreateWindowSurface(m_display, m_config, window, nullptr);
    if (surface == EGL_NO_SURFACE)
    {
        EGLint error = consume_egl_error_or_default(EGL_BAD_SURFACE, TAG);
        RiveLogE(TAG,
                 "eglCreateWindowSurface() failed: %s",
                 EGLErrorString(error).c_str());
    }
    return surface;
}

EGLResult EGLThreadState::swapBuffers()
{
    if (m_currentSurface == EGL_NO_SURFACE)
    {
        // `EGL_NO_SURFACE` is our local sentinel (not an eglGetError() value),
        // so map it to `EGL_BAD_SURFACE` to keep EGLResult.error in EGL-error
        // space.
        return EGLResult::Failure(EGLResult::FailureOperation::swapBuffers,
                                  EGL_BAD_SURFACE);
    }
    if (!eglSwapBuffers(m_display, m_currentSurface))
    {
        return EGLResult::Failure(
            EGLResult::FailureOperation::swapBuffers,
            consume_egl_error_or_default(EGL_BAD_SURFACE, TAG));
    }
    return EGLResult::Ok();
}

EGLResult EGLThreadState::recoverAfterContextLoss()
{
    RiveLogI(TAG, "Attempting EGL context recovery.");
    teardownEGLState();
    return initializeEGLState();
}
} // namespace rive_android
