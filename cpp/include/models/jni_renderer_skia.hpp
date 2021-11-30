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

#include "helpers/tracer.hpp"
#include "helpers/egl_thread_state.hpp"
#include "helpers/rendering_stats.hpp"
#include "helpers/worker_thread.hpp"

using namespace std::chrono_literals;

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

		jobject mKtRenderer;

		SkCanvas* mGpuCanvas;
		rive::SkiaRenderer* mSkRenderer;
		ITracer* mTracer;

	public:
		JNIRendererSkia(jobject ktObject, bool trace = false) :
		    mKtRenderer(getJNIEnv()->NewWeakGlobalRef(ktObject))
		{
			bool traceAvailable = android_get_device_api_level() >= 23;

			if (trace && traceAvailable)
			{
				mTracer = new Tracer();
			}
			else if (trace && !traceAvailable)
			{
				LOGE("JNIRendererSkia cannot enable tracing on API <23. Api "
				     "version is %d",
				     android_get_device_api_level());
				mTracer = new NoopTracer();
			}
			else
			{
				mTracer = new NoopTracer();
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
			if (mTracer)
			{
				delete mTracer;
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
			    });
		}

		void initialize() override
		{
			pthread_setname_np(pthread_self(), "JNIRendererSkia");
			mWorkerThread->run(
			    [=](EGLThreadState* threadState)
			    {
				    jclass ktClass = getJNIEnv()->GetObjectClass(mKtRenderer);
				    threadState->setKtRendererClass(ktClass);
			    });
		}

		void doFrame(long frameTimeNs)
		{
			mWorkerThread->run(
			    [=](EGLThreadState* threadState)
			    {
				    auto env = getJNIEnv();
				    float elapsedMs =
				        (frameTimeNs - threadState->mLastUpdate) / 1e9f;
				    threadState->mLastUpdate = frameTimeNs;
				    env->CallVoidMethod(mKtRenderer,
				                        threadState->mKtAdvanceCallback,
				                        elapsedMs);
				    draw(threadState);
			    });
		}

		void start()
		{
			mWorkerThread->run(
			    [=](EGLThreadState* threadState)
			    {
				    threadState->mIsStarted = true;
				    threadState->mLastUpdate = EGLThreadState::getNowNs();
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
		// should be called once per draw as this function maintains the time
		// delta between calls
		void calculateFps()
		{
			mTracer->beginSection("calculateFps()");
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
			mTracer->endSection();
		}

		void draw(EGLThreadState* threadState)
		{

			// Don't render if we have no surface
			if (threadState->hasNoSurface())
			{
				LOGE("Has No Surface!");
				// Sleep a bit so we don't churn too fast
				std::this_thread::sleep_for(50ms);
				mGpuCanvas = nullptr;
				return;
			}

			auto gpuSurface = threadState->getSkSurface();
			if (!gpuSurface)
			{
				LOGE("No GPU Surface?!");
				std::this_thread::sleep_for(500ms);
				mGpuCanvas = nullptr;
				return;
			}

			mTracer->beginSection("draw()");
			// calculateFps();

			mGpuCanvas->drawColor(SK_ColorTRANSPARENT, SkBlendMode::kClear);
			auto env = getJNIEnv();
			// Kotlin callback.
			env->CallVoidMethod(mKtRenderer, threadState->mKtDrawCallback);

			mTracer->beginSection("flush()");
			threadState->getGrContext()->flush();
			mTracer->endSection(); // flush

			mTracer->beginSection("swapBuffers()");
			threadState->swapBuffers();
			mTracer->endSection(); // swapyBuffers

			mTracer->endSection(); // draw()
		}
	};
} // namespace rive_android
#endif