#include "helpers/general.hpp"
#include "helpers/egl_thread_state.hpp"

#include "SkImageInfo.h"
#include "SkColorSpace.h"
#include "GrBackendSurface.h"
#include "gl/GrGLInterface.h"
#include "gl/GrGLAssembleInterface.h"

namespace rive_android
{
EGLThreadState::EGLThreadState()
{
    mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGL_ERR_CHECK();
    eglInitialize(mDisplay, 0, 0);
    EGL_ERR_CHECK();

    const EGLint configAttributes[] = {EGL_RENDERABLE_TYPE,
                                       EGL_OPENGL_ES2_BIT,
                                       EGL_BLUE_SIZE,
                                       8,
                                       EGL_GREEN_SIZE,
                                       8,
                                       EGL_RED_SIZE,
                                       8,
                                       EGL_DEPTH_SIZE,
                                       16,
                                       EGL_ALPHA_SIZE,
                                       8,
                                       EGL_NONE};

    EGLint numConfigs = 0;
    eglChooseConfig(mDisplay, configAttributes, nullptr, 0, &numConfigs);
    EGL_ERR_CHECK();

    std::vector<EGLConfig> supportedConfigs(static_cast<size_t>(numConfigs));
    eglChooseConfig(mDisplay, configAttributes, supportedConfigs.data(), numConfigs, &numConfigs);
    EGL_ERR_CHECK();

    // Choose a config, either a match if possible or the first config
    // otherwise

    const auto configMatches = [&](EGLConfig config) {
        if (!configHasAttribute(mConfig, EGL_RED_SIZE, 8))
            return false;
        if (!configHasAttribute(mConfig, EGL_GREEN_SIZE, 8))
            return false;
        if (!configHasAttribute(mConfig, EGL_BLUE_SIZE, 8))
            return false;
        return configHasAttribute(mConfig, EGL_DEPTH_SIZE, 16);
    };

    const auto configIter =
        std::find_if(supportedConfigs.cbegin(), supportedConfigs.cend(), configMatches);

    mConfig = (configIter != supportedConfigs.cend()) ? *configIter : supportedConfigs[0];

    const EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};

    mContext = eglCreateContext(mDisplay, mConfig, nullptr, contextAttributes);
    EGL_ERR_CHECK();

    glEnable(GL_CULL_FACE);
    glEnable(GL_DEPTH_TEST);
}

EGLThreadState::~EGLThreadState()
{
    clearSurface();
    if (mContext != EGL_NO_CONTEXT)
    {
        eglDestroyContext(mDisplay, mContext);
        EGL_ERR_CHECK();
    }
    if (mDisplay != EGL_NO_DISPLAY)
    {
        eglTerminate(mDisplay);
        EGL_ERR_CHECK();
    }
    if (mKtRendererClass != nullptr)
        getJNIEnv()->DeleteWeakGlobalRef(mKtRendererClass);
    eglReleaseThread();
    EGL_ERR_CHECK();
}

void EGLThreadState::flush() const
{
    if (!mSkContext)
    {
        LOGE("Cannot flush() without a context.");
        return;
    }
    mSkSurface->flushAndSubmit();
}

void EGLThreadState::clearSurface()
{
    if (mSurface == EGL_NO_SURFACE)
    {
        return;
    }

    makeCurrent(EGL_NO_SURFACE);
    eglDestroySurface(mDisplay, mSurface);
    EGL_ERR_CHECK();
    mSurface = EGL_NO_SURFACE;
    auto sfcPtr = mSkSurface.release();
    delete sfcPtr;
    auto ctxPtr = mSkContext.release();
    delete ctxPtr;
    mSkSurface = nullptr;
    mSkContext = nullptr;
}

bool EGLThreadState::configHasAttribute(EGLConfig config, EGLint attribute, EGLint value) const
{
    EGLint outValue = 0;
    EGLBoolean result = eglGetConfigAttrib(mDisplay, mConfig, attribute, &outValue);
    return result && (outValue == value);
}

sk_sp<GrDirectContext> EGLThreadState::createSkiaContext()
{
    if (!makeCurrent(mSurface))
    {
        LOGE("Unable to eglMakeCurrent");
        mSurface = EGL_NO_SURFACE;
        return nullptr;
    }

    auto get_string = reinterpret_cast<PFNGLGETSTRINGPROC>(getProcAddress("glGetString"));

    if (!get_string)
    {
        LOGE("get_string() failed");
        return nullptr;
    }

    auto c_version = reinterpret_cast<const char*>(get_string(GL_VERSION));
    if (c_version == nullptr)
    {
        LOGE("c_version failed");
        return nullptr;
    }

    auto get_proc = [](void* context, const char name[]) -> GrGLFuncPtr {
        return reinterpret_cast<GrGLFuncPtr>(
            reinterpret_cast<EGLThreadState*>(context)->getProcAddress(name));
    };
    std::string version(c_version);
    auto interface = version.find("OpenGL ES") == std::string::npos
                         ? GrGLMakeAssembledGLInterface(this, get_proc)
                         : GrGLMakeAssembledGLESInterface(this, get_proc);
    if (!interface)
    {
        LOGE("Failed to find the interface version!?");
        return nullptr;
    }
    mSkContext = GrDirectContext::MakeGL(interface);
    return mSkContext;
}

sk_sp<SkSurface> EGLThreadState::createSkiaSurface()
{
    static GrGLFramebufferInfo fbInfo = {};
    fbInfo.fFBOID = 0u;
    fbInfo.fFormat = GL_RGBA8;

    GrBackendRenderTarget backendRenderTarget(mWidth, mHeight, 1, 8, fbInfo);
    static SkSurfaceProps surfaceProps(0, kUnknown_SkPixelGeometry);

    mSkSurface = SkSurface::MakeFromBackendRenderTarget(getSkiaContext().get(),
                                                        backendRenderTarget,
                                                        kBottomLeft_GrSurfaceOrigin,
                                                        kRGBA_8888_SkColorType,
                                                        SkColorSpace::MakeSRGB(),
                                                        &surfaceProps,
                                                        nullptr,
                                                        nullptr);

    if (!mSkSurface)
    {
        LOGE("Failed to get GPU Surface?!");
        return nullptr;
    }

    return mSkSurface;
}

void* EGLThreadState::getProcAddress(const char* name)
{
    if (name == nullptr)
    {
        return nullptr;
    }

    auto symbol = eglGetProcAddress(name);
    EGL_ERR_CHECK();
    if (symbol == nullptr)
    {
        LOGE("Couldn't fetch symbol name for: %s", name);
    }

    return reinterpret_cast<void*>(symbol);
}

void EGLThreadState::swapBuffers() const
{
    if (mDisplay == EGL_NO_DISPLAY || mSurface == EGL_NO_SURFACE)
    {
        LOGE("Swapping buffers without a display/surface");
        return;
    }
    eglSwapBuffers(mDisplay, mSurface);
    EGL_ERR_CHECK();
}

bool EGLThreadState::setWindow(ANativeWindow* window)
{
    clearSurface();
    if (!window)
    {
        return false;
    }

    mSurface = eglCreateWindowSurface(mDisplay, mConfig, window, nullptr);
    EGL_ERR_CHECK();
    ANativeWindow_release(window);

    if (!createSkiaContext())
    {
        LOGE("Unable to create Skia context.");
        mSurface = EGL_NO_SURFACE;
        return false;
    }

    mWidth = ANativeWindow_getWidth(window);
    mHeight = ANativeWindow_getHeight(window);

    // Width/Height getters return negative values on error.
    // Probably a race condition with surfaces being reclaimed by the OS before
    // this function completes.
    if (mWidth < 0 || mHeight < 0)
    {
        LOGE("Window is unavailable.");
        return false;
    }

    LOGI("Set up window surface %dx%d", mWidth, mHeight);

    auto gpuSurface = createSkiaSurface();
    if (!gpuSurface)
    {
        LOGE("Unable to create a SkSurface??");
        mSurface = EGL_NO_SURFACE;
        return false;
    }

    return true;
}
} // namespace rive_android