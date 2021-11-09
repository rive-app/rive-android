#ifndef _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_

#include "skia_renderer.hpp"
#include "GrBackendSurface.h"
#include "GrDirectContext.h"
#include "SkCanvas.h"
#include "SkSurface.h"
#include "SkImageInfo.h"
#include "gl/GrGLInterface.h"
#include <jni.h>

namespace rive_android
{
  class JNIRendererSkia : virtual public IJNIRenderer
  {
  private:
    int mWidth, mHeight;
    sk_sp<GrDirectContext> mContext;
    GrGLFramebufferInfo mFramebufferInfo;
    SkCanvas *mCanvas;
    SkSurface *mSurface;

  public:
    jobject jRendererObject;

    JNIRendererSkia()
    {
      LOGD("Constructing Renderer...");
      mFramebufferInfo.fFBOID = 0;
      mFramebufferInfo.fFormat = GL_RGBA8;
    }

    ~JNIRendererSkia()
    {
      getJNIEnv()->DeleteGlobalRef(jRendererObject);
    }

    void initialize() override
    {
      GrContextOptions options;
      mContext = GrDirectContext::MakeGL(nullptr, options);
      LOGD("I should have a context now? %p", mContext.get());
    }

    void setViewport(int width, int height)
    {
      LOGD("Setting viewport: %d, %d", width, height);
      if (width != mWidth || height != mHeight)
      {
        mWidth = width;
        mHeight = height;
        SkColorType colorType = kRGBA_8888_SkColorType;

        GrBackendRenderTarget backendRenderTarget(
            mWidth,
            mHeight,
            0, // sample count
            0, // stencil bits
            mFramebufferInfo);

        mSurface = SkSurface::MakeFromBackendRenderTarget(
                       mContext.get(),
                       backendRenderTarget,
                       kBottomLeft_GrSurfaceOrigin,
                       colorType,
                       nullptr,
                       nullptr)
                       .release();
        if (mSurface == nullptr)
        {
          LOGE("Failed to create Skia surface\n");
        }
        else
        {
          mCanvas = mSurface->getCanvas();
          LOGD("I got the freaking canvas alright!!");
        }
      }
    }

    void startFrame() const
    {
      // Clear screen.
      SkPaint paint;
      paint.setColor(SK_ColorDKGRAY);
      mCanvas->drawPaint(paint);
    }

    int width() const { return mWidth; }
    int height() const { return mHeight; }

    SkCanvas* canvas() const { return mCanvas; }
    void flush() const { mContext->flush(); }

    rive::RenderPaint *makeRenderPaint() override
    {
      return new rive::SkiaRenderPaint();
    }
    rive::RenderPath *makeRenderPath() override
    {
      return new rive::SkiaRenderPath();
    }
  };
} // namespace rive_android
#endif