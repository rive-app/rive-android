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

#include "helpers/EGLThreadState.h"
#include "helpers/Stats.hpp"
#include "helpers/WorkerThread.h"

using namespace std::chrono_literals;

namespace rive_android
{
  class JNIRendererSkia : virtual public IJNIRenderer
  {
  private:
    float mAverageFps = -1.0f;

    ANativeWindow *nWindow = nullptr;
    rive::Artboard *mArtboard = nullptr;
    rive::LinearAnimationInstance *mInstance = nullptr;
    rive::SkiaRenderer *mSkRenderer;

    WorkerThread<EGLThreadState> mWorkerThread =
        {"SwappyRenderer", Affinity::Odd};

    // Mean and variance for the pipeline frame time.
    RenderingStats mFrameTimeStats = RenderingStats(
        20 /* number of samples to average over */
    );

    typedef void *(*fp_ATrace_beginSection)(const char *sectionName);
    typedef void *(*fp_ATrace_endSection)(void);
    typedef void *(*fp_ATrace_isEnabled)(void);

    void *(*ATrace_beginSection)(const char *sectionName);
    void *(*ATrace_endSection)(void);
    void *(*ATrace_isEnabled)(void);

  public:
    jobject jRendererObject;

    JNIRendererSkia()
    {
      // Native Trace API is supported in API level 23
      void *lib = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
      if (lib != NULL)
      {
        //  Retrieve function pointers from shared object.
        ATrace_beginSection =
            reinterpret_cast<fp_ATrace_beginSection>(
                dlsym(lib, "ATrace_beginSection"));
        ATrace_endSection =
            reinterpret_cast<fp_ATrace_endSection>(
                dlsym(lib, "ATrace_endSection"));
        ATrace_isEnabled =
            reinterpret_cast<fp_ATrace_isEnabled>(
                dlsym(lib, "ATrace_isEnabled"));
      }
      auto result = (bool)ATrace_isEnabled();
      // LOGI("Is tracing enabled? %d", result);
      pthread_setname_np(pthread_self(), "JNIRendererSkia");
    }

    ~JNIRendererSkia()
    {
      getJNIEnv()->DeleteGlobalRef(jRendererObject);
      if (mArtboard)
        delete mArtboard;
    }

    rive::RenderPaint *makeRenderPaint() override
    {
      return new rive::SkiaRenderPaint();
    }

    rive::RenderPath *makeRenderPath() override
    {
      return new rive::SkiaRenderPath();
    }

    void setWindow(ANativeWindow *window)
    {
      mWorkerThread
          .run([=](EGLThreadState *threadState)
               {
                 nWindow = window;
                 if (!threadState->setWindow(window))
                 {
                   LOGE("Failed to set window");
                   return;
                 }

                 auto gpuSurface = threadState->getSkSurface();
                 mSkRenderer = new rive::SkiaRenderer(gpuSurface->getCanvas());
               });
    }

    void setArtboard(rive::Artboard *ab)
    {
      if (ab == mArtboard)
      {
        return;
      }
      if (mArtboard)
      {
        delete mArtboard;
      }
      mArtboard = ab;
      mArtboard->advance(0.0f);
      if (mArtboard->animationCount() > 0)
      {
        mInstance = new rive::LinearAnimationInstance(mArtboard->firstAnimation());
        mInstance->advance(0.0f);
      }
    }

    void initialize() override {}

    void startFrame()
    {
      mWorkerThread
          .run([=](EGLThreadState *threadState)
               {
                 threadState->mIsStarted = true;
                 // Reset time to avoid super-large update of position
                 threadState->mLastUpdate = std::chrono::steady_clock::now();
                 requestDraw();
               });
    }

    SkCanvas *canvas() const
    {
      // TODO: this can probably be removed.
      return nullptr;
    }

    void flush() const
    {
      // TODO: this can probably be removed too..
      // mContext->flush();
    }

    void stop()
    {
      mWorkerThread
          .run([=](EGLThreadState *threadState)
               { threadState->mIsStarted = false; });
    }

    int width() const
    {
      return nWindow ? ANativeWindow_getWidth(nWindow) : -1;
    }

    int height() const
    {
      return nWindow ? ANativeWindow_getHeight(nWindow) : -1;
    }

    float averageFps() const { return mAverageFps; }
    RenderingStats &frameTimeStats() { return mFrameTimeStats; }

  private:
    void requestDraw()
    {
      mWorkerThread.run(
          [=](EGLThreadState *threadState)
          {
            if (threadState->mIsStarted)
              draw(threadState);
          });
    }

    // should be called once per draw as this function maintains the time delta between calls
    void calculateFps()
    {
      ATrace_beginSection("calculateFps()");
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

      ATrace_endSection();
    }

    void draw(EGLThreadState *threadState)
    {
      ATrace_beginSection("swappyDraw()");

      // Don't render if we have no surface
      if (threadState->hasNoSurface())
      {
        // Sleep a bit so we don't churn too fast
        std::this_thread::sleep_for(50ms);
        requestDraw();
        return;
      }

      auto gpuSurface = threadState->getSkSurface();
      if (!gpuSurface)
      {
        LOGE("No GPU Surface?!");
        std::this_thread::sleep_for(500ms);
        requestDraw();
        return;
      }

      threadState->recordFrameStart();

      calculateFps();

      float deltaSeconds = threadState->mSwapIntervalNS / 1e9f;
      if (threadState->mLastUpdate - std::chrono::steady_clock::now() <= 100ms)
      {
        deltaSeconds = (threadState->mLastUpdate - std::chrono::steady_clock::now()).count() / 1e9f;
      }
      threadState->mLastUpdate = std::chrono::steady_clock::now();

      int w = threadState->mWidth;
      int h = threadState->mHeight;

      SkCanvas *gpuCanvas = gpuSurface->getCanvas();
      float elapsed = -1.0f * deltaSeconds;
      gpuCanvas->drawColor(SK_ColorTRANSPARENT, SkBlendMode::kClear);
      if (mArtboard != nullptr)
      {
        if (mInstance)
        {
          mInstance->advance(elapsed);
          mInstance->apply(mArtboard);
        }
        mArtboard->advance(elapsed);
        mSkRenderer->save();
        mSkRenderer->align(
            rive::Fit::contain,
            rive::Alignment::center,
            rive::AABB(0, 0, w, h),
            mArtboard->bounds());
        mArtboard->draw(mSkRenderer);
        mSkRenderer->restore();
      }

      threadState->getGrContext()->flush();
      // const float aspectRatio = static_cast<float>(threadState->mWidth) / threadState->mHeight;

      threadState->swapBuffers();

      // If we're still started, request another frame
      requestDraw();
      ATrace_endSection();
    }
  };
} // namespace rive_android
#endif