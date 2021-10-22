#ifndef _RIVE_ANDROID_EGL_THREAD_STATE_H_
#define _RIVE_ANDROID_EGL_THREAD_STATE_H_

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include "Settings.h"

#include "GrDirectContext.h"
#include "SkSurface.h"

namespace rive_android
{
  class EGLThreadState
  {
  public:
    EGLThreadState();
    ~EGLThreadState();

    void *getProcAddress(const char *name) const;
    void onSettingsChanged(const Settings *settings);
    void clearSurface();
    bool configHasAttribute(EGLConfig config, EGLint attribute, EGLint value);

    sk_sp<GrDirectContext> createGrContext();
    sk_sp<SkSurface> createSkSurface();

    EGLBoolean makeCurrent(EGLSurface surface)
    {
      return eglMakeCurrent(mDisplay, surface, surface, mContext);
    }

    sk_sp<GrDirectContext> getGrContext()
    {
      if (mSkContext)
      {
        return mSkContext;
      }

      return createGrContext();
    }

    sk_sp<SkSurface> getSkSurface()
    {
      if (mSkSurface)
      {
        return mSkSurface;
      }

      return createSkSurface();
    }

    bool hasNoSurface() const
    {
      return mSurface == EGL_NO_SURFACE || mSkSurface == nullptr;
    }

    EGLDisplay mDisplay = EGL_NO_DISPLAY;
    EGLConfig mConfig = static_cast<EGLConfig>(0);
    EGLSurface mSurface = EGL_NO_SURFACE;
    EGLContext mContext = EGL_NO_CONTEXT;

    sk_sp<GrDirectContext> mSkContext = nullptr;
    sk_sp<SkSurface> mSkSurface = nullptr;

    bool mIsStarted = false;

    std::chrono::time_point<std::chrono::steady_clock> mLastUpdate =
        std::chrono::steady_clock::now();

    std::chrono::nanoseconds mRefreshPeriod = std::chrono::nanoseconds{0};
    int64_t mSwapIntervalNS = 0;
    int32_t mWidth = 0;
    int32_t mHeight = 0;
  };
}

#endif