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

#include "helpers/WorkerThread.h"

using namespace std::chrono_literals;

namespace rive_android
{
  class JNIRendererSkia : virtual public IJNIRenderer
  {
  private:
    bool mSwappyEnabled = true;

    float averageFps = -1.0f;
    samples::WorkerThread<samples::ThreadState> mWorkerThread =
        {"Renderer", samples::Affinity::Odd};

    samples::WorkerThread<samples::HotPocketState> mHotPocketThread =
        {"HotPocket", samples::Affinity::Even};

    // sk_sp<GrDirectContext> mContext = nullptr;
    // SkSurface *mSurface = nullptr;
    ANativeWindow *nWindow = nullptr;
    rive::Artboard *mArtboard = nullptr;
    rive::LinearAnimationInstance *mInstance = nullptr;
    rive::SkiaRenderer *mSkRenderer;

    void spin()
    {
      mHotPocketThread
          .run([this](samples::HotPocketState *hotPocketState)
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
          .run([=](samples::ThreadState *threadState)
               {
                 threadState->clearSurface();
                 nWindow = window;

                 if (!window)
                 {
                   SwappyGL_setWindow(nullptr);
                   return;
                 }

                 threadState->surface =
                     eglCreateWindowSurface(threadState->display, threadState->config, window, NULL);
                 ANativeWindow_release(window);
                 //  if (!threadState->makeCurrent(threadState->surface))
                 if (!threadState->createGrContext())
                 {
                   LOGE("Unable to eglMakeCurrent");
                   threadState->surface = EGL_NO_SURFACE;
                   return;
                 }

                 int width = ANativeWindow_getWidth(window);
                 int height = ANativeWindow_getHeight(window);

                 LOGI("Set up window surface %dx%d", width, height);

                 SwappyGL_setWindow(window);

                 threadState->width = width;
                 threadState->height = height;
                 auto gpuSurface = threadState->createSkSurface();
                 if (!gpuSurface)
                 {
                   LOGE("Unable to create a SkSurface??");
                   threadState->surface = EGL_NO_SURFACE;
                   return;
                 }

                 mSkRenderer = new rive::SkiaRenderer(gpuSurface->getCanvas());
               });
    }

    // void surfaceSetup(samples::ThreadState *tState, int width, int height)
    // {
    //   int cWidth = tState->width;
    //   int cHeight = tState->height;
    //   LOGI("Setting up the surface?!");
    //   if (cWidth != width || cHeight != height)
    //   {
    //     LOGI("From %dx%d\n\t to %dx%d", cWidth, cHeight, width, height);
    //     tState->width = width;
    //     tState->height = height;

    //     auto get_proc = [](void *context, const char name[]) -> GrGLFuncPtr
    //     {
    //       return reinterpret_cast<GrGLFuncPtr>(
    //           reinterpret_cast<JNIRendererSkia *>(context)->getProcAddress(name));
    //     };
    //     auto get_string =
    //         reinterpret_cast<PFNGLGETSTRINGPROC>(getProcAddress("glGetString"));
    //     auto c_version = reinterpret_cast<const char *>(get_string(GL_VERSION));
    //     std::string version(c_version);
    //     auto interface = version.find("OpenGL ES") == std::string::npos
    //                          ? GrGLMakeAssembledGLInterface(this, get_proc)
    //                          : GrGLMakeAssembledGLESInterface(this, get_proc);
    //     if (!interface)
    //     {
    //       LOGE("Failed to find the interface version!?");
    //     }
    //     mContext = GrDirectContext::MakeGL(interface);
    //     if (!mContext)
    //     {
    //       LOGE("Failed to make context!?");
    //     }
    //   }
    // }

    void *getProcAddress(const char *name) const
    {
      // LOGD("Getting proc address for %s", name);
      if (name == nullptr)
      {
        return nullptr;
      }

      auto symbol = eglGetProcAddress(name);
      if (symbol == NULL)
      {
        LOGE("Couldn't fetch symbol name for: %s", name);
      }

      return reinterpret_cast<void *>(symbol);
    }

    void initialize() override
    {
      // TODO:
    }

    void setViewport(int width, int height)
    {
      // TODO:
    }

    void startFrame()
    {
      mSwappyEnabled = SwappyGL_isEnabled();

      mWorkerThread
          .run([this](samples::ThreadState *threadState)
               {
                 threadState->isStarted = true;
                 // Reset time to avoid super-large update of position
                 threadState->lastUpdate = std::chrono::steady_clock::now();
                 requestDraw();
               });
      mHotPocketThread
          .run([this](samples::HotPocketState *hotPocketState)
               { hotPocketState->isStarted = true; });
      spin();
    }

    // TODO: stop() threads.

    SkCanvas *canvas() const
    {
      return nullptr; // TODO: figure this out too.
    }

    void flush() const
    {
      // TODO: figure this out?
      // mContext->flush();
    }

    void stop()
    {
      mWorkerThread
          .run([=](samples::ThreadState *threadState)
               { threadState->isStarted = false; });
    }

    void requestDraw()
    {
      mWorkerThread.run(
          [=](samples::ThreadState *threadState)
          {
            if (threadState->isStarted)
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
        averageFps = fpsSum / fpsCount;
        fpsSum = 0;
        fpsCount = 0;
      }
      prev = now;
    }

    void draw(samples::ThreadState *threadState)
    {
      // Don't render if we have no surface
      if (threadState->surface == EGL_NO_SURFACE)
      {
        // Sleep a bit so we don't churn too fast
        std::this_thread::sleep_for(50ms);
        requestDraw();
        return;
      }

      if (mSwappyEnabled)
        SwappyGL_recordFrameStart(threadState->display, threadState->surface);

      calculateFps();

      float deltaSeconds = threadState->swapIntervalNS / 1e9f;
      if (threadState->lastUpdate - std::chrono::steady_clock::now() <= 100ms)
      {
        deltaSeconds = (threadState->lastUpdate - std::chrono::steady_clock::now()).count() / 1e9f;
      }
      threadState->lastUpdate = std::chrono::steady_clock::now();

      threadState->x += threadState->velocity * deltaSeconds;

      if (threadState->x > 0.8f)
      {
        threadState->velocity *= -1.0f;
        threadState->x = 1.6f - threadState->x;
      }
      else if (threadState->x < -0.8f)
      {
        threadState->velocity *= -1.0f;
        threadState->x = -1.6f - threadState->x;
      }

      {
        int w = threadState->width;
        int h = threadState->height;

        // static GrGLFramebufferInfo fbInfo = {};
        // fbInfo.fFBOID = 0u;
        // fbInfo.fFormat = GL_RGBA8;

        // static GrBackendRenderTarget backendRenderTarget(
        //     w, h,
        //     1, 8,
        //     fbInfo);

        // auto get_proc = [](void *context, const char name[]) -> GrGLFuncPtr
        // {
        //   return reinterpret_cast<GrGLFuncPtr>(
        //       reinterpret_cast<JNIRendererSkia *>(context)->getProcAddress(name));
        // };
        // auto get_string =
        //     reinterpret_cast<PFNGLGETSTRINGPROC>(getProcAddress("glGetString"));
        // auto c_version = reinterpret_cast<const char *>(get_string(GL_VERSION));
        // std::string version(c_version);
        // auto interface = version.find("OpenGL ES") == std::string::npos
        //                      ? GrGLMakeAssembledGLInterface(this, get_proc)
        //                      : GrGLMakeAssembledGLESInterface(this, get_proc);
        // if (!interface)
        // {
        //   LOGE("Failed to find the interface version!?");
        // }
        // auto gpuContext = GrDirectContext::MakeGL(interface);
        // if (!gpuContext)
        // {
        //   LOGE("Failed to make context!?");
        // }

        // static SkSurfaceProps surfaceProps(0, kUnknown_SkPixelGeometry);

        // TODO: this *really* needs to be cached. But when I try, it fails.
        // sk_sp<SkSurface> gpuSurface = SkSurface::MakeFromBackendRenderTarget(
        //     threadState->getGrContext().get(),
        //     backendRenderTarget,
        //     kBottomLeft_GrSurfaceOrigin,
        //     kN32_SkColorType,
        //     SkColorSpace::MakeSRGB(),
        //     &surfaceProps,
        //     nullptr,
        //     nullptr);

        auto gpuSurface = threadState->getSkSurface();
        // auto gpuSurface = threadState->createSkSurface();

        if (!gpuSurface)
        {
          LOGE("Failed to get GPU Surface?!");
        }
        else
        {
          SkCanvas *gpuCanvas = gpuSurface->getCanvas();
          // gpuCanvas->save();
          // const SkScalar scale = 256.0f;
          // const SkScalar R = 0.45f * scale;
          // const SkScalar TAU = 6.2831853f;
          // SkPath path;
          // for (int i = 0; i < 5; ++i)
          // {
          //   SkScalar theta = 2 * i * TAU / 5;
          //   if (i == 0)
          //   {
          //     path.moveTo(R * cos(theta), R * sin(theta));
          //   }
          //   else
          //   {
          //     path.lineTo(R * cos(theta), R * sin(theta));
          //   }
          // }
          // path.close();
          // static float elapsed = 0.0;
          // elapsed -= deltaSeconds;
          // float zeroToOne = abs(sin(elapsed));
          // path.transform(SkMatrix::RotateDeg(zeroToOne * 360.0f));
          // path.transform(SkMatrix::Translate(w * zeroToOne, h * zeroToOne));
          // SkPaint p;
          // p.setAntiAlias(true);
          // gpuCanvas->clear(SK_ColorWHITE);
          // gpuCanvas->translate(0.5f * scale, 0.5f * scale);
          // gpuCanvas->drawPath(path, p);
          // gpuCanvas->flush();
          // gpuCanvas->restore();
          
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
        }
      }
      // const float aspectRatio = static_cast<float>(threadState->width) / threadState->height;

      // glClearColor(0.7f, 0.5f, 0.3f, 1.0f);
      // glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
      if (mSwappyEnabled)
      {
        SwappyGL_swap(threadState->display, threadState->surface);
      }
      else
      {
        eglSwapBuffers(threadState->display, threadState->surface);
      }

      // If we're still started, request another frame
      requestDraw();
    }

#define CASE_STR(value) \
  case value:           \
    return #value;
    const char *eglGetErrorString(EGLint error)
    {
      switch (error)
      {
        CASE_STR(EGL_SUCCESS)
        CASE_STR(EGL_NOT_INITIALIZED)
        CASE_STR(EGL_BAD_ACCESS)
        CASE_STR(EGL_BAD_ALLOC)
        CASE_STR(EGL_BAD_ATTRIBUTE)
        CASE_STR(EGL_BAD_CONTEXT)
        CASE_STR(EGL_BAD_CONFIG)
        CASE_STR(EGL_BAD_CURRENT_SURFACE)
        CASE_STR(EGL_BAD_DISPLAY)
        CASE_STR(EGL_BAD_SURFACE)
        CASE_STR(EGL_BAD_MATCH)
        CASE_STR(EGL_BAD_PARAMETER)
        CASE_STR(EGL_BAD_NATIVE_PIXMAP)
        CASE_STR(EGL_BAD_NATIVE_WINDOW)
        CASE_STR(EGL_CONTEXT_LOST)
      default:
        return "Unknown";
      }
    }
#undef CASE_STR

    int width() const
    {
      return nWindow ? ANativeWindow_getWidth(nWindow) : -1;
    }
    int height() const { return nWindow ? ANativeWindow_getHeight(nWindow) : -1; }

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
  };
} // namespace rive_android
#endif