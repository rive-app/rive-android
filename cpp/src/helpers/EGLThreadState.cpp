#include "helpers/general.hpp"
#include "helpers/EGLThreadState.h"
#include "swappy/swappyGL.h"

#include "SkImageInfo.h"
#include "GrBackendSurface.h"
#include "gl/GrGLInterface.h"
#include "gl/GrGLAssembleInterface.h"

namespace rive_android
{
  EGLThreadState::EGLThreadState()
  {
    mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(mDisplay, 0, 0);

    const EGLint configAttributes[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE, 8,
        EGL_DEPTH_SIZE, 16,
        EGL_ALPHA_SIZE, 8, 
        EGL_NONE};

    EGLint numConfigs = 0;
    eglChooseConfig(
        mDisplay,
        configAttributes,
        nullptr,
        0,
        &numConfigs);
    std::vector<EGLConfig> supportedConfigs(static_cast<size_t>(numConfigs));
    eglChooseConfig(mDisplay, configAttributes, supportedConfigs.data(), numConfigs, &numConfigs);

    // Choose a config, either a match if possible or the first config otherwise

    const auto configMatches = [&](EGLConfig config)
    {
      if (!configHasAttribute(mConfig, EGL_RED_SIZE, 8))
        return false;
      if (!configHasAttribute(mConfig, EGL_GREEN_SIZE, 8))
        return false;
      if (!configHasAttribute(mConfig, EGL_BLUE_SIZE, 8))
        return false;
      return configHasAttribute(mConfig, EGL_DEPTH_SIZE, 16);
    };

    const auto configIter = std::find_if(
        supportedConfigs.cbegin(), supportedConfigs.cend(),
        configMatches);

    mConfig = (configIter != supportedConfigs.cend()) ? *configIter : supportedConfigs[0];

    const EGLint contextAttributes[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE};

    mContext = eglCreateContext(mDisplay, mConfig, nullptr, contextAttributes);

    glEnable(GL_CULL_FACE);
    glEnable(GL_DEPTH_TEST);
  }

  EGLThreadState::~EGLThreadState()
  {
    clearSurface();
    if (mContext != EGL_NO_CONTEXT)
      eglDestroyContext(mDisplay, mContext);
    if (mDisplay != EGL_NO_DISPLAY)
      eglTerminate(mDisplay);
    if (mKtRendererClass != nullptr)
      getJNIEnv()->DeleteWeakGlobalRef(mKtRendererClass);
  }

  void EGLThreadState::onSettingsChanged(const Settings *settings)
  {
    mRefreshPeriod = settings->getRefreshPeriod();
    mSwapIntervalNS = settings->getSwapIntervalNS();
  }

  void EGLThreadState::clearSurface()
  {
    if (mSurface == EGL_NO_SURFACE)
    {
      return;
    }

    makeCurrent(EGL_NO_SURFACE);
    eglDestroySurface(mDisplay, mSurface);
    mSurface = EGL_NO_SURFACE;
  }

  bool EGLThreadState::configHasAttribute(EGLConfig config, EGLint attribute, EGLint value)
  {
    EGLint outValue = 0;
    EGLBoolean result = eglGetConfigAttrib(mDisplay, mConfig, attribute, &outValue);
    return result && (outValue == value);
  }

  sk_sp<GrDirectContext> EGLThreadState::createGrContext()
  {
    if (!makeCurrent(mSurface))
    {
      LOGE("Unable to eglMakeCurrent");
      mSurface = EGL_NO_SURFACE;
      return nullptr;
    }

    auto get_string =
        reinterpret_cast<PFNGLGETSTRINGPROC>(getProcAddress("glGetString"));

    if (!get_string)
    {
      LOGE("get_string() failed");
      return nullptr;
    }

    auto c_version = reinterpret_cast<const char *>(get_string(GL_VERSION));
    if (c_version == NULL)
    {
      LOGE("c_version failed");
      return nullptr;
    }

    auto get_proc = [](void *context, const char name[]) -> GrGLFuncPtr
    {
      return reinterpret_cast<GrGLFuncPtr>(
          reinterpret_cast<EGLThreadState *>(context)->getProcAddress(name));
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

  sk_sp<SkSurface> EGLThreadState::createSkSurface()
  {
    static GrGLFramebufferInfo fbInfo = {};
    fbInfo.fFBOID = 0u;
    fbInfo.fFormat = GL_RGBA8;

    static GrBackendRenderTarget backendRenderTarget(
        mWidth, mHeight,
        1, 8,
        fbInfo);
    static SkSurfaceProps surfaceProps(0, kUnknown_SkPixelGeometry);

    mSkSurface = SkSurface::MakeFromBackendRenderTarget(
        getGrContext().get(),
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

  void *EGLThreadState::getProcAddress(const char *name) const
  {
    if (name == nullptr)
    {
      return nullptr;
    }

    auto symbol = eglGetProcAddress(name);
    if (symbol == NULL)
    {
      LOGE("Couldn't fetch symbol name for: %s", name);
    }

    return reinterpret_cast<void *>(symbol);
  }

  void EGLThreadState::swapBuffers()
  {
    if (mIsSwappyEnabled)
    {
      SwappyGL_swap(mDisplay, mSurface);
    }
    else
    {
      eglSwapBuffers(mDisplay, mSurface);
    }
  }

  bool EGLThreadState::setWindow(ANativeWindow *window)
  {
    clearSurface();
    if (!window)
    {
      SwappyGL_setWindow(nullptr);
      return false;
    }

    mSurface =
        eglCreateWindowSurface(mDisplay, mConfig, window, NULL);
    ANativeWindow_release(window);

    if (!createGrContext())
    {
      LOGE("Unable to eglMakeCurrent");
      mSurface = EGL_NO_SURFACE;
      return false;
    }

    int width = ANativeWindow_getWidth(window);
    int height = ANativeWindow_getHeight(window);

    //  LOGI("Set up window surface %dx%d", width, height);
    SwappyGL_setWindow(window);

    mWidth = width;
    mHeight = height;
    auto gpuSurface = createSkSurface();
    if (!gpuSurface)
    {
      LOGE("Unable to create a SkSurface??");
      mSurface = EGL_NO_SURFACE;
      return false;
    }

    return true;
  }
}