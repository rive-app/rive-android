#ifndef _RIVE_ANDROID_EGL_THREAD_STATE_H_
#define _RIVE_ANDROID_EGL_THREAD_STATE_H_

#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>
#include <chrono>

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

		void onSettingsChanged(const Settings*);
		bool setWindow(ANativeWindow*);
		void swapBuffers() const;

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

		static long getNowNs()
		{
			using namespace std::chrono;
			// Reset time to avoid super-large update of position
			auto nowNs = time_point_cast<nanoseconds>(steady_clock::now());
			return nowNs.time_since_epoch().count();
		}

		float getElapsedMs(long frameTimeNs) const
		{
			float elapsedMs = (frameTimeNs - mLastUpdate) / 1e9f;
			return elapsedMs;
		}

		bool mIsStarted = false;
		jmethodID mKtDrawCallback = nullptr;
		jmethodID mKtAdvanceCallback = nullptr;

		// Last update time in nanoseconds
		long mLastUpdate = 0;

	private:
		EGLDisplay mDisplay = EGL_NO_DISPLAY;
		EGLConfig mConfig = static_cast<EGLConfig>(0);
		EGLSurface mSurface = EGL_NO_SURFACE;
		EGLContext mContext = EGL_NO_CONTEXT;

		sk_sp<GrDirectContext> mSkContext = nullptr;
		sk_sp<SkSurface> mSkSurface = nullptr;

		int32_t mWidth = 0;
		int32_t mHeight = 0;

		jclass mKtRendererClass = nullptr;

		sk_sp<GrDirectContext> createGrContext();
		sk_sp<SkSurface> createSkSurface();
		static void* getProcAddress(const char*);
		void clearSurface();
		bool configHasAttribute(EGLConfig, EGLint, EGLint) const;

		EGLBoolean makeCurrent(EGLSurface surface) const
		{
			if (mDisplay == EGL_NO_DISPLAY)
			{
				LOGE("No display!?");
			}
			if (mContext == EGL_NO_CONTEXT)
			{
				LOGE("No context!?");
			}
			return eglMakeCurrent(mDisplay, surface, surface, mContext);
		}
	};
} // namespace rive_android

#endif