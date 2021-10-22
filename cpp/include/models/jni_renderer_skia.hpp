#ifndef _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_

#include <thread>
#include <EGL/egl.h>
#include <android/native_window.h>
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
#include "swappy/swappyGL.h"
#include "swappy/swappyGL_extra.h"

#include "helpers/EGLThreadState.h"
#include "helpers/WorkerThread.h"

using namespace std::chrono_literals;

namespace rive_android
{
  class JNIRendererSkia : virtual public IJNIRenderer
  {
  private:
    bool mSwappyEnabled = true;
    float mAverageFps = -1.0f;

    ANativeWindow *nWindow = nullptr;
    rive::Artboard *mArtboard = nullptr;
    rive::LinearAnimationInstance *mInstance = nullptr;
    rive::SkiaRenderer *mSkRenderer;

    WorkerThread<EGLThreadState> mWorkerThread =
        {"Renderer", Affinity::Odd};

    WorkerThread<HotPocketState> mHotPocketThread =
        {"HotPocket", Affinity::Even};

  public:
    jobject jRendererObject;

    JNIRendererSkia() {}
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
                 threadState->clearSurface();
                 nWindow = window;

                 if (!window)
                 {
                   SwappyGL_setWindow(nullptr);
                   return;
                 }

                 threadState->mSurface =
                     eglCreateWindowSurface(threadState->mDisplay, threadState->mConfig, window, NULL);
                 ANativeWindow_release(window);

                 if (!threadState->createGrContext())
                 {
                   LOGE("Unable to eglMakeCurrent");
                   threadState->mSurface = EGL_NO_SURFACE;
                   return;
                 }

                 int width = ANativeWindow_getWidth(window);
                 int height = ANativeWindow_getHeight(window);

                 //  LOGI("Set up window surface %dx%d", width, height);
                 SwappyGL_setWindow(window);

                 threadState->mWidth = width;
                 threadState->mHeight = height;
                 auto gpuSurface = threadState->createSkSurface();
                 if (!gpuSurface)
                 {
                   LOGE("Unable to create a SkSurface??");
                   threadState->mSurface = EGL_NO_SURFACE;
                   return;
                 }

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
      mSwappyEnabled = SwappyGL_isEnabled();

      mWorkerThread
          .run([=](EGLThreadState *threadState)
               {
                 threadState->mIsStarted = true;
                 // Reset time to avoid super-large update of position
                 threadState->mLastUpdate = std::chrono::steady_clock::now();
                 requestDraw();
               });
      mHotPocketThread
          .run([=](HotPocketState *hotPocketState)
               { hotPocketState->isStarted = true; });
      spin();
    }

    SkCanvas *canvas() const
    {
      // TODO: this can probably be removed.
      return nullptr;
    }

    void flush() const
    {
      // TODO: this can probably be removed too..      // mContext->flush();
    }

    void stop()
    {
      mWorkerThread
          .run([=](EGLThreadState *threadState)
               { threadState->mIsStarted = false; });
      mHotPocketThread
          .run([](HotPocketState *hotPocketState)
               { hotPocketState->isStarted = false; });
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
      static constexpr int FPS_SAMPLES = 10;
      static std::chrono::steady_clock::time_point prev = std::chrono::steady_clock::now();
      static float fpsSum = 0;
      static int fpsCount = 0;

      std::chrono::steady_clock::time_point now = std::chrono::steady_clock::now();
      fpsSum += 1.0f / ((now - prev).count() / 1e9f);
      fpsCount++;
      if (fpsCount == FPS_SAMPLES)
      {
        mAverageFps = fpsSum / fpsCount;
        fpsSum = 0;
        fpsCount = 0;
      }
      prev = now;
    }

    void draw(EGLThreadState *threadState)
    {
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

      if (mSwappyEnabled)
        SwappyGL_recordFrameStart(threadState->mDisplay, threadState->mSurface);

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

      if (mSwappyEnabled)
      {
        SwappyGL_swap(threadState->mDisplay, threadState->mSurface);
      }
      else
      {
        eglSwapBuffers(threadState->mDisplay, threadState->mSurface);
      }

      // If we're still started, request another frame
      requestDraw();
    }

    void spin()
    {
      mHotPocketThread
          .run([this](HotPocketState *hotPocketState)
               {
                 if (!hotPocketState->isEnabled || !hotPocketState->isStarted)
                   return;
                 for (int i = 1; i < 1000; ++i)
                 {
                   int value = i;
                   while (value != 1)
                   {
                     if (value == 0)
                     {
                       LOGI("This will never run, but hopefully the compiler doesn't notice");
                     }
                     if (value % 2 == 0)
                     {
                       value /= 2;
                     }
                     else
                     {
                       value = 3 * value + 1;
                     }
                   }
                 }
                 spin();
               });
    }
  };
} // namespace rive_android
#endif