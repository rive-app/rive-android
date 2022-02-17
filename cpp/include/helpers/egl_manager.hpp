#ifndef _RIVE_ANDROID_EGL_MANAGER_H_
#define _RIVE_ANDROID_EGL_MANAGER_H_

#include <mutex>
#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

namespace rive_android
{
	class EGLManager
	{
	private:
		EGLManager();
		~EGLManager();

		static EGLManager* mInstance;
		static std::mutex mMutex;

		bool configHasAttribute(EGLConfig config,
		                        EGLint attribute,
		                        EGLint value) const;

		void clearSurface(EGLSurface surface);

		EGLDisplay mDisplay = EGL_NO_DISPLAY;
		EGLConfig mConfig = static_cast<EGLConfig>(0);

	public:
		// Singleton getter.
		static EGLManager* getInstance();
		// Singleton can't be copied/assigned.
		EGLManager(EGLManager const&) = delete;
		void operator=(EGLManager const&) = delete;

		EGLContext createContext() const;
		EGLBoolean destroyContext(EGLContext ctx) const;
		EGLSurface createWindowSurface(ANativeWindow* window) const;
		EGLBoolean makeCurrent(EGLSurface surface,
		                       EGLContext ctx = EGL_NO_CONTEXT) const;
		void destroySurface(EGLSurface surface) const;
		void swapBuffers(EGLSurface surface) const;
	};
} // namespace rive_android

#endif