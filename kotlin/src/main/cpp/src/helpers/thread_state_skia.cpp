#include "helpers/thread_state_egl.hpp"
#include "helpers/thread_state_skia.hpp"
#include "gl/GrGLAssembleInterface.h"

namespace rive_android
{
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

SkiaThreadState::~SkiaThreadState()
{
    // Release Skia Context if has been init'd.
    if (m_skContext.get())
    {
        m_skContext->abandonContext();
        m_skContext.reset(nullptr);
    }
}

sk_sp<SkSurface> SkiaThreadState::createSkiaSurface(EGLSurface eglSurface, int width, int height)
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

void SkiaThreadState::destroySurface(EGLSurface eglSurface)
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

void SkiaThreadState::makeCurrent(EGLSurface eglSurface)
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
} // namespace rive_android
