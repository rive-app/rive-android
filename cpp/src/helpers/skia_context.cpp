#include "helpers/general.hpp"
#include "helpers/skia_context.hpp"
#include "gl/GrGLInterface.h"
#include "gl/GrGLAssembleInterface.h"
#include "SkSurface.h"

namespace rive_android
{
// Instantiate static objects.
std::weak_ptr<SkiaContextManager> SkiaContextManager::mInstance;
std::mutex SkiaContextManager::mMutex;

std::shared_ptr<SkiaContextManager> SkiaContextManager::getInstance()
{
    std::lock_guard<std::mutex> lock(mMutex);
    std::shared_ptr<SkiaContextManager> sharedInstance = mInstance.lock();
    if (!sharedInstance)
    {
        LOGI("📦 CREATING INSTANCE!!");
        sharedInstance.reset(new SkiaContextManager, [](SkiaContextManager* p) { delete p; });
        mInstance = sharedInstance;
    }
    else
    {
        LOGI("🫱 FETCHED INSTANCE?! %ld", mInstance.use_count());
    }
    return sharedInstance;
}
SkiaContextManager::SkiaContextManager()
{
    mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (mDisplay == EGL_NO_DISPLAY)
    {
        EGL_ERR_CHECK();
        LOGE("NO DISPLAY!?");
    }

    if (!eglInitialize(mDisplay, 0, 0))
    {
        EGL_ERR_CHECK();
        LOGE("FAILED TO INIT");
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
                                       16,
                                       EGL_ALPHA_SIZE,
                                       8,
                                       EGL_NONE};

    EGLint numConfigs = 0;
    if (!eglChooseConfig(mDisplay, configAttributes, nullptr, 0, &numConfigs))
    {
        EGL_ERR_CHECK();
        LOGE("DIDNT FIND ANY CONFIG? %d", numConfigs);
    }

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
    if (mContext == EGL_NO_CONTEXT)
    {
        LOGE("DID NOT GET A CONTEXT?!");
        EGL_ERR_CHECK();
    }

    glEnable(GL_CULL_FACE);
    glEnable(GL_DEPTH_TEST);
}

SkiaContextManager::~SkiaContextManager()
{
    // Release Skia Context if has been init'd.
    if (mSkContext.get())
    {
        mSkContext->abandonContext();
        mSkContext.reset(nullptr);
    }

    if (mContext != EGL_NO_CONTEXT)
    {
        eglDestroyContext(mDisplay, mContext);
        EGL_ERR_CHECK();
    }

    eglReleaseThread();
    EGL_ERR_CHECK();

    if (mDisplay != EGL_NO_DISPLAY)
    {
        eglTerminate(mDisplay);
        EGL_ERR_CHECK();
    }
}

void* SkiaContextManager::getProcAddress(const char* name)
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

void SkiaContextManager::makeSkiaContext()
{
    auto get_string = reinterpret_cast<PFNGLGETSTRINGPROC>(getProcAddress("glGetString"));

    if (!get_string)
    {
        LOGE("get_string() failed");
        return;
    }

    LOGI("c_version()");
    auto c_version = reinterpret_cast<const char*>(get_string(GL_VERSION));
    if (c_version == nullptr)
    {
        EGL_ERR_CHECK();
        LOGE("c_version failed");
        return;
    }

    auto get_proc = [](void* context, const char name[]) -> GrGLFuncPtr {
        return reinterpret_cast<GrGLFuncPtr>(
            reinterpret_cast<SkiaContextManager*>(context)->getProcAddress(name));
    };
    std::string version(c_version);
    auto interface = version.find("OpenGL ES") == std::string::npos
                         ? GrGLMakeAssembledGLInterface(this, get_proc)
                         : GrGLMakeAssembledGLESInterface(this, get_proc);
    LOGI("OpenGL Version %s", version.c_str());
    if (!interface)
    {
        LOGE("Failed to find the interface version!?");
        return;
    }
    mSkContext = GrDirectContext::MakeGL(interface);
    LOGI("Skia Context Created %p", mSkContext.get());
}

sk_sp<SkSurface> SkiaContextManager::createSkiaSurface(int32_t width, int32_t height)
{
    static GrGLFramebufferInfo fbInfo = {};
    fbInfo.fFBOID = 0u;
    fbInfo.fFormat = GL_RGBA8;

    GrBackendRenderTarget backendRenderTarget(width, height, 1, 8, fbInfo);
    static SkSurfaceProps surfaceProps(0, kUnknown_SkPixelGeometry);

    LOGI("SkSurface::MakeFromBackendRenderTarget");
    auto skSurface = SkSurface::MakeFromBackendRenderTarget(getSkiaContext(),
                                                            backendRenderTarget,
                                                            kBottomLeft_GrSurfaceOrigin,
                                                            kRGBA_8888_SkColorType,
                                                            SkColorSpace::MakeSRGB(),
                                                            &surfaceProps,
                                                            nullptr,
                                                            nullptr);

    if (!skSurface)
    {
        LOGE("Failed to get GPU Surface?!");
        return nullptr;
    }

    return skSurface;
}

GrDirectContext* SkiaContextManager::getSkiaContext()
{
    if (mSkContext == nullptr)
    {
        makeSkiaContext();
    }
    return mSkContext.get();
}

EGLSurface SkiaContextManager::getWindowSurface(ANativeWindow* window)
{
    auto res = eglCreateWindowSurface(mDisplay, mConfig, window, nullptr);
    EGL_ERR_CHECK();
    return res;
}

EGLBoolean SkiaContextManager::makeCurrent(EGLSurface surface /* = EGL_NO_SURFACE */)
{
    auto ctx = surface == EGL_NO_SURFACE ? EGL_NO_CONTEXT : mContext;
    EGLBoolean res = eglMakeCurrent(mDisplay, surface, surface, ctx);
    EGL_ERR_CHECK();
    return res;
}

bool SkiaContextManager::configHasAttribute(EGLConfig config, EGLint attribute, EGLint value) const
{
    EGLint outValue = 0;
    EGLBoolean result = eglGetConfigAttrib(mDisplay, mConfig, attribute, &outValue);
    EGL_ERR_CHECK();
    return result && (outValue == value);
}

} // namespace rive_android