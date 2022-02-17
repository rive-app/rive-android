#include <cassert>

#include "helpers/egl_thread_state.hpp"
#include "helpers/egl_manager.hpp"

#include "SkImageInfo.h"
#include "GrBackendSurface.h"
#include "gl/GrGLInterface.h"
#include "gl/GrGLAssembleInterface.h"

namespace rive_android
{
	EGLThreadState::EGLThreadState()
	{
		mContext = EGLManager::getInstance()->createContext();
	}

	EGLThreadState::~EGLThreadState()
	{
		LOGI("~EGLThreadState()");
		clearSurface();
		assert(mContext != EGL_NO_CONTEXT);
		EGLManager::getInstance()->destroyContext(mContext);

		if (mKtRendererClass != nullptr)
		{
			getJNIEnv()->DeleteWeakGlobalRef(mKtRendererClass);
		}
		LOGI("~EGLThreadState() end");
	}

	void EGLThreadState::onSettingsChanged(const Settings* settings) {}

	void EGLThreadState::clearSurface()
	{
		if (mSurface == EGL_NO_SURFACE)
		{
			return;
		}
		EGLManager::getInstance()->destroySurface(mSurface);
		mSurface = EGL_NO_SURFACE;
	}

	sk_sp<GrDirectContext> EGLThreadState::createGrContext()
	{
		LOGI("createGrContext()");
		assert(mSurface != EGL_NO_SURFACE);

		bool success =
		    EGLManager::getInstance()->makeCurrent(mSurface, mContext);
		if (!success)
		{
			LOGE("Unable to makeCurrent()");
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

		auto c_version = reinterpret_cast<const char*>(get_string(GL_VERSION));
		if (c_version == nullptr)
		{
			LOGE("c_version failed");
			return nullptr;
		}

		auto get_proc = [](void* context, const char name[]) -> GrGLFuncPtr
		{
			return reinterpret_cast<GrGLFuncPtr>(
			    reinterpret_cast<EGLThreadState*>(context)->getProcAddress(
			        name));
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
		LOGI("createSkSurface()");
		static GrGLFramebufferInfo fbInfo = {};
		fbInfo.fFBOID = 0u;
		fbInfo.fFormat = GL_RGBA8;

		GrBackendRenderTarget backendRenderTarget(
		    mWidth, mHeight, 1, 8, fbInfo);
		static SkSurfaceProps surfaceProps(SkSurfaceProps::kDynamicMSAA_Flag,
		                                   kUnknown_SkPixelGeometry);

		mSkSurface =
		    SkSurface::MakeFromBackendRenderTarget(getGrContext().get(),
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

		// TODO: move this and its caller into EGLManager.
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
		LOGI("swapBuffers()");
		if (mSurface == EGL_NO_SURFACE)
		{
			LOGW("Trying to swap without a surface!");
			return;
		}
		EGLManager::getInstance()->swapBuffers(mSurface);
	}

	bool EGLThreadState::setWindow(ANativeWindow* window)
	{
		LOGI("setWindow()");
		clearSurface();
		if (!window)
		{
			return false;
		}

		mSurface = EGLManager::getInstance()->createWindowSurface(window);

		mWidth = ANativeWindow_getWidth(window);
		mHeight = ANativeWindow_getHeight(window);

		LOGI("Set up window surface %dx%d", mWidth, mHeight);
		if (!createGrContext())
		{
			LOGE("Unable to createGrContext");
			mSurface = EGL_NO_SURFACE;
			return false;
		}

		auto gpuSurface = createSkSurface();
		if (!gpuSurface)
		{
			LOGE("Unable to create a SkSurface??");
			mSurface = EGL_NO_SURFACE;
			return false;
		}

		return true;
	}
} // namespace rive_android