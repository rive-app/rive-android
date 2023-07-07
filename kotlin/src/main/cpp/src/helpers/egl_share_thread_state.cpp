#include <thread>
#include <vector>

#include "helpers/egl_share_thread_state.hpp"

#include "GrDirectContext.h"
#include "gl/GrGLInterface.h"
#include "gl/GrGLAssembleInterface.h"
#include "SkSurface.h"

#if defined(DEBUG) || defined(LOG)
#define EGL_ERR_CHECK() _check_egl_error(__FILE__, __LINE__)
#else
#define EGL_ERR_CHECK()
#endif

namespace rive_android
{
static bool config_has_attribute(EGLDisplay display,
                                 EGLConfig config,
                                 EGLint attribute,
                                 EGLint value)
{
    EGLint outValue = 0;
    EGLBoolean result = eglGetConfigAttrib(display, config, attribute, &outValue);
    EGL_ERR_CHECK();
    return result && (outValue == value);
}

EGLShareThreadState::EGLShareThreadState()
{
    m_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (m_display == EGL_NO_DISPLAY)
    {
        EGL_ERR_CHECK();
        LOGE("NO DISPLAY!?");
        return;
    }

    if (!eglInitialize(m_display, 0, 0))
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

    // Choose a config, either a match if possible or the first config
    // otherwise
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

    const auto configIter =
        std::find_if(supportedConfigs.cbegin(), supportedConfigs.cend(), configMatches);

    m_config = (configIter != supportedConfigs.cend()) ? *configIter : supportedConfigs[0];

    const EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};

    m_context = eglCreateContext(m_display, m_config, nullptr, contextAttributes);
    if (m_context == EGL_NO_CONTEXT)
    {
        LOGE("eglCreateContext() failed.");
        EGL_ERR_CHECK();
    }
}

EGLShareThreadState::~EGLShareThreadState()
{
    LOGD("EGLThreadState getting destroyed! 🧨");

    // Release Skia Context if has been init'd.
    if (m_skContext.get())
    {
        m_skContext->abandonContext();
        m_skContext.reset(nullptr);
    }

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

EGLSurface EGLShareThreadState::createEGLSurface(ANativeWindow* window)
{
    if (!window)
    {
        return EGL_NO_SURFACE;
    }

    LOGD("mSkiaContextManager.createWindowSurface()");
    auto res = eglCreateWindowSurface(m_display, m_config, window, nullptr);
    EGL_ERR_CHECK();
    return res;
}

void EGLShareThreadState::destroySurface(EGLSurface eglSurface)
{
    if (eglSurface == EGL_NO_SURFACE)
    {
        return;
    }

    if (m_currentSurface == eglSurface)
    {
        makeCurrent(EGL_NO_SURFACE);
    }
    eglDestroySurface(m_display, eglSurface);
    EGL_ERR_CHECK();
}

static sk_sp<GrDirectContext> make_skia_context()
{
    LOGI("c_version()");
    auto c_version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    if (c_version == nullptr)
    {
        EGL_ERR_CHECK();
        LOGE("c_version failed");
        return nullptr;
    }

    auto get_proc = [](void* context, const char name[]) -> GrGLFuncPtr {
        return reinterpret_cast<GrGLFuncPtr>(eglGetProcAddress(name));
    };
    std::string version(c_version);
    auto interface = version.find("OpenGL ES") == std::string::npos
                         ? GrGLMakeAssembledGLInterface(nullptr, get_proc)
                         : GrGLMakeAssembledGLESInterface(nullptr, get_proc);
    LOGI("OpenGL Version %s", version.c_str());
    if (!interface)
    {
        LOGE("GrGLMakeAssembledGL(ES)Interface failed.");
        return nullptr;
    }
    auto ctx = GrDirectContext::MakeGL(interface);
    LOGI("Skia Context Created %p", ctx.get());
    return ctx;
}

sk_sp<SkSurface> EGLShareThreadState::createSkiaSurface(EGLSurface eglSurface,
                                                        int width,
                                                        int height)
{
    // Width/Height getters return negative values on error.
    // Probably a race condition with surfaces being reclaimed by the OS before
    // this function completes.
    if (width < 0 || height < 0)
    {
        LOGE("Window is unavailable.");
        return nullptr;
    }

    makeCurrent(eglSurface);

    LOGI("Set up window surface %dx%d", width, height);
    if (m_skContext == nullptr)
    {
        m_skContext = make_skia_context();
        if (m_skContext == nullptr)
        {
            return nullptr;
        }
    }

    static GrGLFramebufferInfo fbInfo = {};
    fbInfo.fFBOID = 0u;
    fbInfo.fFormat = GL_RGBA8;

    GrBackendRenderTarget backendRenderTarget(width, height, 1, 8, fbInfo);
    static SkSurfaceProps surfaceProps(0, kUnknown_SkPixelGeometry);

    auto skSurface = SkSurface::MakeFromBackendRenderTarget(m_skContext.get(),
                                                            backendRenderTarget,
                                                            kBottomLeft_GrSurfaceOrigin,
                                                            kRGBA_8888_SkColorType,
                                                            nullptr,
                                                            &surfaceProps,
                                                            nullptr,
                                                            nullptr);

    if (!skSurface)
    {
        LOGE("SkSurface::MakeFromBackendRenderTarget() failed.");
        return nullptr;
    }

    return skSurface;
}

void EGLShareThreadState::makeCurrent(EGLSurface eglSurface)
{
    if (eglSurface == m_currentSurface)
    {
        return;
    }
    auto ctx = eglSurface == EGL_NO_SURFACE ? EGL_NO_CONTEXT : m_context;
    eglMakeCurrent(m_display, eglSurface, eglSurface, ctx);
    m_currentSurface = eglSurface;
    EGL_ERR_CHECK();
}

void EGLShareThreadState::swapBuffers()
{
    eglSwapBuffers(m_display, m_currentSurface);
    EGL_ERR_CHECK();
}
} // namespace rive_android
