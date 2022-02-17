#include <vector>
#include <cassert>
#include "helpers/general.hpp"
#include "helpers/egl_manager.hpp"

namespace rive_android
{
	// Initialize static variables.
	EGLManager* EGLManager::mInstance{nullptr};
	std::mutex EGLManager::mMutex;

	EGLManager* EGLManager::getInstance()
	{
		std::lock_guard<std::mutex> lock(mMutex);
		if (mInstance == nullptr)
		{
			mInstance = new EGLManager();
		}

		return mInstance;
	}

	EGLManager::EGLManager()
	{
		LOGI("EGLManager");
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

		std::vector<EGLConfig> supportedConfigs(
		    static_cast<size_t>(numConfigs));
		eglChooseConfig(mDisplay,
		                configAttributes,
		                supportedConfigs.data(),
		                numConfigs,
		                &numConfigs);
		EGL_ERR_CHECK();

		// Choose a config, either a match if possible or the first config
		// otherwise
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
		    supportedConfigs.cbegin(), supportedConfigs.cend(), configMatches);

		mConfig = (configIter != supportedConfigs.cend()) ? *configIter
		                                                  : supportedConfigs[0];

		LOGI("EGLManager ====>");
	}
	bool EGLManager::configHasAttribute(EGLConfig config,
	                                    EGLint attribute,
	                                    EGLint value) const
	{
		EGLint outValue = 0;
		EGLBoolean result =
		    eglGetConfigAttrib(mDisplay, mConfig, attribute, &outValue);
		if (!(result && outValue == value))
		{
			LOGE("Did not match: %d (wanted %d)", outValue, value);
		}
		// This check can get spammy.
		// EGL_ERR_CHECK();

		return result && (outValue == value);
	}

	EGLManager::~EGLManager()
	{
		LOGI("~EGLManager");
		std::lock_guard<std::mutex> instanceLock(mMutex);
		if (mDisplay != EGL_NO_DISPLAY)
		{
			LOGD("eglTerminate(mDisplay)");
			if (EGL_TRUE != eglTerminate(mDisplay))
			{
				LOGE("I could not terminate the display!!");
			}
			EGL_ERR_CHECK();
			mDisplay = EGL_NO_DISPLAY;
		}
		LOGI("~EGLManager ===>");
	}

	EGLContext EGLManager::createContext() const
	{
		const EGLint contextAttributes[] = {
		    EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};

		auto context = eglCreateContext(
		    mDisplay, mConfig, EGL_NO_CONTEXT, contextAttributes);
		EGL_ERR_CHECK();

		glEnable(GL_CULL_FACE);
		EGL_ERR_CHECK();
		glEnable(GL_DEPTH_TEST);
		EGL_ERR_CHECK();
		return context;
	}

	EGLBoolean EGLManager::destroyContext(EGLContext ctx) const
	{
		LOGD("destroyContext(ctx)");
		assert(ctx != nullptr && ctx != EGL_NO_CONTEXT);

		EGLBoolean res = EGL_FALSE;
		LOGD("eglDestroyContext(mDisplay, ctx)");
		res = eglDestroyContext(mDisplay, ctx);
		if (EGL_TRUE != res)
		{
			LOGE("I could not destroy the context!!");
		}

		LOGD("eglReleaseThread()");
		eglReleaseThread();
		EGL_ERR_CHECK();
		return res;
	}

	EGLSurface EGLManager::createWindowSurface(ANativeWindow* window) const
	{
		if (!window)
		{
			return nullptr;
		}

		EGLSurface surface =
		    eglCreateWindowSurface(mDisplay, mConfig, window, nullptr);
		EGL_ERR_CHECK();
		return surface;
	}

	void EGLManager::destroySurface(EGLSurface surface) const
	{
		LOGI("destroySurface(surface)");

		assert(mDisplay != EGL_NO_DISPLAY);
		assert(surface != EGL_NO_SURFACE);

		LOGI("makeCurrent(EGL_NO_SURFACE, EGL_NO_CONTEXT)");
		makeCurrent(EGL_NO_SURFACE, EGL_NO_CONTEXT);

		LOGI("eglDestroySfc(mDisplay, surface)");
		if (EGL_TRUE != eglDestroySurface(mDisplay, surface))
		{
			LOGE("I could not destroy the surface");
		}
		EGL_ERR_CHECK();
	}

	EGLBoolean EGLManager::makeCurrent(EGLSurface surface, EGLContext ctx) const
	{
		assert(mDisplay != EGL_NO_DISPLAY);

		LOGI("Making current");
		EGLBoolean success = eglMakeCurrent(mDisplay, surface, surface, ctx);
		EGL_ERR_CHECK();
		return success;
	}

	void EGLManager::swapBuffers(EGLSurface surface) const
	{
		assert(mDisplay != EGL_NO_DISPLAY);
		if (surface == EGL_NO_SURFACE)
		{
			LOGE("Trying to swap without a display!");
			return;
		}
		LOGI("eglSwapBuffers(display, mSurface)");
		eglSwapBuffers(mDisplay, surface);
		EGL_ERR_CHECK();
	}
} // namespace rive_android