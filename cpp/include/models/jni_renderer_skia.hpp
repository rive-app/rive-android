#ifndef _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_

#include <dlfcn.h>

#include <thread>
#include <pthread.h>
#include <EGL/egl.h>
#include <android/native_window.h>
#include <android/trace.h>
#include <GLES3/gl3.h>
#include <jni.h>

#include "rive/artboard.hpp"
#include "rive/animation/linear_animation_instance.hpp"

#include "jni_renderer.hpp"
#include "skia_renderer.hpp"
#include "GrBackendSurface.h"
#include "GrDirectContext.h"
#include "SkCanvas.h"
#include "SkSurface.h"
#include "SkImageInfo.h"
#include "gl/GrGLInterface.h"
#include "gl/GrGLAssembleInterface.h"

#include "helpers/egl_thread_state.hpp"
#include "helpers/rendering_stats.hpp"
#include "helpers/worker_thread.hpp"

using namespace std::chrono_literals;

// TODO:
// - Move tracing function
//    - in initializer?
//    - in custom tracer object?

namespace rive_android
{
	class JNIRendererSkia : virtual public IJNIRenderer
	{
	private:
		float mAverageFps = -1.0f;

		ANativeWindow* nWindow = nullptr;

		WorkerThread<EGLThreadState>* mWorkerThread =
		    new WorkerThread<EGLThreadState>("EGLRenderer", Affinity::Odd);

		// Mean and variance for the pipeline frame time.
		RenderingStats mFrameTimeStats =
		    RenderingStats(20 /* number of samples to average over */
		    );

		typedef void* (*fp_ATrace_beginSection)(const char* sectionName);
		typedef void* (*fp_ATrace_endSection)(void);
		typedef void* (*fp_ATrace_isEnabled)(void);

		void* (*ATrace_beginSection)(const char* sectionName);
		void* (*ATrace_endSection)(void);
		void* (*ATrace_isEnabled)(void);

		jobject mKtRenderer;

		SkCanvas* mGpuCanvas;
		rive::SkiaRenderer* mSkRenderer;

	public:
		JNIRendererSkia(jobject ktObject) :
		    mKtRenderer(getJNIEnv()->NewWeakGlobalRef(ktObject))
		{
			// Native Trace API is supported in API level 23
			void* lib = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
			if (lib != NULL)
			{
				//  Retrieve function pointers from shared object.
				ATrace_beginSection = reinterpret_cast<fp_ATrace_beginSection>(
				    dlsym(lib, "ATrace_beginSection"));
				ATrace_endSection = reinterpret_cast<fp_ATrace_endSection>(
				    dlsym(lib, "ATrace_endSection"));
				ATrace_isEnabled = reinterpret_cast<fp_ATrace_isEnabled>(
				    dlsym(lib, "ATrace_isEnabled"));

				assert(ATrace_beginSection);
				assert(ATrace_endSection);
				assert(ATrace_isEnabled);
			}
			initialize();
		}

		~JNIRendererSkia()
		{
			// Make sure the thread is removed before the Global Ref.
			delete mWorkerThread;
			getJNIEnv()->DeleteWeakGlobalRef(mKtRenderer);
			if (mSkRenderer)
			{
				delete mSkRenderer;
			}
		}

		rive::RenderPaint* makeRenderPaint() override
		{
			return new rive::SkiaRenderPaint();
		}

		rive::RenderPath* makeRenderPath() override
		{
			return new rive::SkiaRenderPath();
		}

		void setWindow(ANativeWindow* window)
		{
			mWorkerThread->run(
			    [=](EGLThreadState* threadState)
			    {
				    if (!threadState->setWindow(window))
				    {
					    if (nWindow)
					    {
						    ANativeWindow_release(nWindow);
					    }
					    return;
				    }

				    ANativeWindow_acquire(window);
				    nWindow = window;

				    auto gpuSurface = threadState->getSkSurface();
				    mGpuCanvas = gpuSurface->getCanvas();
				    mSkRenderer = new rive::SkiaRenderer(mGpuCanvas);
						// Draw the first frame.
				    draw(threadState);
			    });
		}

		void initialize() override
		{
			// auto result = (bool)ATrace_isEnabled();
			pthread_setname_np(pthread_self(), "JNIRendererSkia");
			mWorkerThread->run(
			    [=](EGLThreadState* threadState)
			    {
				    jclass ktClass = getJNIEnv()->GetObjectClass(mKtRenderer);
				    threadState->setKtRendererClass(ktClass);
			    });
		}

		void doFrame()
		{
			mWorkerThread->run([=](EGLThreadState* threadState)
			                   { requestDraw(); });
		}

		void start()
		{
			mWorkerThread->run(
			    [=](EGLThreadState* threadState)
			    {
				    // Reset time to avoid super-large update of position
				    threadState->mLastUpdate = std::chrono::steady_clock::now();
				    threadState->mIsStarted = true;
			    });
		}

		void stop()
		{
			mWorkerThread->run([=](EGLThreadState* threadState)
			                   { threadState->mIsStarted = false; });
		}

		SkCanvas* canvas() const { return mGpuCanvas; }
		rive::SkiaRenderer* skRenderer() const { return mSkRenderer; }
		float averageFps() const { return mAverageFps; }

		RenderingStats& frameTimeStats() { return mFrameTimeStats; }
		int width() const
		{
			return nWindow ? ANativeWindow_getWidth(nWindow) : -1;
		}

		int height() const
		{
			return nWindow ? ANativeWindow_getHeight(nWindow) : -1;
		}

	private:
		void requestDraw()
		{
			mWorkerThread->run(
			    [=](EGLThreadState* threadState)
			    {
				    if (threadState->mIsStarted)
				    {
					    draw(threadState);
				    }
			    });
		}

		// should be called once per draw as this function maintains the time
		// delta between calls
		void calculateFps()
		{
			traceStart("calculateFps()");
			static constexpr int FPS_SAMPLES = 10;
			static std::chrono::steady_clock::time_point prev =
			    std::chrono::steady_clock::now();
			static float fpsSum = 0;
			static int fpsCount = 0;

			std::chrono::steady_clock::time_point now =
			    std::chrono::steady_clock::now();

			fpsSum += 1.0f / ((now - prev).count() / 1e9f);
			fpsCount++;
			if (fpsCount == FPS_SAMPLES)
			{
				mAverageFps = fpsSum / fpsCount;
				fpsSum = 0;
				fpsCount = 0;
			}
			prev = now;

			traceEnd();
		}

		void drawCallback(float elapsed, EGLThreadState* threadState)
		{
			auto env = getJNIEnv();
			env->CallVoidMethod(
			    mKtRenderer, threadState->mKtAdvanceCallback, elapsed);
			env->CallVoidMethod(mKtRenderer, threadState->mKtDrawCallback);
		}

		void traceStart(const char* sectionName)
		{
			// ATrace_beginSection(sectionName);
		}

		void traceEnd()
		{
			// ATrace_endSection();
		}

		void draw(EGLThreadState* threadState)
		{
			traceStart("draw()");
			// Don't render if we have no surface
			if (threadState->hasNoSurface())
			{
				// Sleep a bit so we don't churn too fast
				std::this_thread::sleep_for(50ms);
				mGpuCanvas = nullptr;
				requestDraw();
				return;
			}

			auto gpuSurface = threadState->getSkSurface();
			if (!gpuSurface)
			{
				LOGE("No GPU Surface?!");
				std::this_thread::sleep_for(500ms);
				mGpuCanvas = nullptr;
				requestDraw();
				return;
			}

			calculateFps();

			float deltaSeconds = threadState->mSwapIntervalNS / 1e9f;
			if (threadState->mLastUpdate - std::chrono::steady_clock::now() <=
			    100ms)
			{
				deltaSeconds = (threadState->mLastUpdate -
				                std::chrono::steady_clock::now())
				                   .count() /
				               1e9f;
			}
			threadState->mLastUpdate = std::chrono::steady_clock::now();

			float elapsed = -1.0f * deltaSeconds;
			mGpuCanvas->drawColor(SK_ColorTRANSPARENT, SkBlendMode::kClear);
			drawCallback(elapsed, threadState);
			threadState->getGrContext()->flush();
			threadState->swapBuffers();

			traceEnd();
		}
	};
} // namespace rive_android
#endif