#ifndef _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_

#include <GLES3/gl3.h>
#include <jni.h>

#include "skia_renderer.hpp"

#include "helpers/tracer.hpp"
#include "helpers/egl_thread_state.hpp"
#include "helpers/worker_thread.hpp"

namespace rive_android
{
	class JNIRendererSkia
	{
	private:
		float mAverageFps = -1.0f;
		jobject mKtRenderer;

		ANativeWindow* nWindow = nullptr;

		WorkerThread<EGLThreadState>* mWorkerThread =
		    new WorkerThread<EGLThreadState>("EGLRenderer", Affinity::Odd);

		SkCanvas* mGpuCanvas;
		rive::SkiaRenderer* mSkRenderer;
		ITracer* mTracer;

	public:
		JNIRendererSkia(jobject ktObject, bool trace = false);

		~JNIRendererSkia();

		void setWindow(ANativeWindow* window);

		void doFrame(long frameTimeNs);

		void start();

		void stop();

		SkCanvas* canvas() const { return mGpuCanvas; }
		rive::SkiaRenderer* skRenderer() const { return mSkRenderer; }
		float averageFps() const { return mAverageFps; }

		int width() const
		{
			return nWindow ? ANativeWindow_getWidth(nWindow) : -1;
		}

		int height() const
		{
			return nWindow ? ANativeWindow_getHeight(nWindow) : -1;
		}

	private:
		void setupThread() const;

		ITracer* getTracer(bool trace) const;

		/**
		 * Calculate FPS over an average of 10 samples
		 */
		void calculateFps();
		void draw(EGLThreadState* threadState);
	};
} // namespace rive_android
#endif