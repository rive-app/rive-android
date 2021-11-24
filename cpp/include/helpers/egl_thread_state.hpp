#ifndef _RIVE_ANDROID_EGL_THREAD_STATE_H_
#define _RIVE_ANDROID_EGL_THREAD_STATE_H_

#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

#include "helpers/general.hpp"
#include "settings.hpp"

#include "GrDirectContext.h"
#include "SkSurface.h"

namespace rive_android
{
	class EGLThreadState
	{
	public:
		EGLThreadState();
		~EGLThreadState();

		void* getProcAddress(const char*) const;
		void onSettingsChanged(const Settings*);
		void clearSurface();
		bool configHasAttribute(EGLConfig, EGLint, EGLint);

		sk_sp<GrDirectContext> createGrContext();
		sk_sp<SkSurface> createSkSurface();

		void swapBuffers();
		bool setWindow(ANativeWindow*);

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

		void setKtRendererClass(jclass localReference)
		{
			auto env = getJNIEnv();
			mKtRendererClass =
			    reinterpret_cast<jclass>(env->NewWeakGlobalRef(localReference));
			mKtDrawCallback = env->GetMethodID(mKtRendererClass, "draw", "()V");
			mKtAdvanceCallback =
			    env->GetMethodID(mKtRendererClass, "advance", "(F)V");
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

		jclass mKtRendererClass = nullptr;
		jmethodID mKtDrawCallback;
		jmethodID mKtAdvanceCallback;
	};
} // namespace rive_android

#endif