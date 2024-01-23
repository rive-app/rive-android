//
// Created by Umberto Sonnino on 7/18/23.
//
#ifndef RIVE_ANDROID_CANVAS_RENDER_OBJECTS_HPP
#define RIVE_ANDROID_CANVAS_RENDER_OBJECTS_HPP

#include "helpers/android_factories.hpp"
#include "helpers/general.hpp"
#include "helpers/worker_ref.hpp"

namespace rive_android
{
class CanvasRenderPath : public rive::RenderPath
{
private:
    rive::FillRule m_FillRule;
    jobject m_ktPath = nullptr;

    static jobject CreatePath();

public:
    CanvasRenderPath();

    CanvasRenderPath(rive::RawPath&, rive::FillRule);

    ~CanvasRenderPath();

    jobject ktPath() const { return m_ktPath; }

    void rewind() override;

    void addRenderPath(rive::RenderPath*, const rive::Mat2D&) override;

    void moveTo(float x, float y) override;

    void lineTo(float x, float y) override;

    void cubicTo(float ox, float oy, float ix, float iy, float x, float y) override;

    void close() override;

    void fillRule(rive::FillRule value) override;
};

class CanvasShader : public rive::RenderShader
{
protected:
    jobject m_KtShader = nullptr;

public:
    CanvasShader() {}

    virtual ~CanvasShader()
    {
        if (m_KtShader != nullptr)
        {
            GetJNIEnv()->DeleteGlobalRef(m_KtShader);
        }
    }

    jobject ktShader() const { return m_KtShader; }
};

class LinearGradientCanvasShader : public CanvasShader
{
public:
    LinearGradientCanvasShader(float sx,
                               float sy,
                               float ex,
                               float ey,
                               const rive::ColorInt colors[], // [count]
                               const float stops[],           // [count]
                               size_t count);
};

class RadialGradientCanvasShader : public CanvasShader
{
public:
    RadialGradientCanvasShader(float cx,
                               float cy,
                               float radius,
                               const rive::ColorInt colors[], // [count]
                               const float stops[],           // [count]
                               size_t count);
};

class CanvasRenderPaint : public rive::RenderPaint
{
private:
    jobject m_ktPaint;

    static void porterDuffBlendMode(jobject, rive::BlendMode);

public:
    CanvasRenderPaint();

    ~CanvasRenderPaint();

    void style(rive::RenderPaintStyle) override;

    void thickness(float) override;

    void join(rive::StrokeJoin) override;

    void color(rive::ColorInt) override;

    void cap(rive::StrokeCap) override;

    void blendMode(rive::BlendMode) override;

    void shader(rive::rcp<rive::RenderShader>) override;

    void invalidateStroke() override {}

    jobject ktPaint() const { return m_ktPaint; }

    static jobject CreateKtPaint();

    static void SetStyle(jobject, rive::RenderPaintStyle);

    static void SetThickness(jobject, float);

    static void SetJoin(jobject, rive::StrokeJoin);

    static void SetColor(jobject, rive::ColorInt);

    static void SetCap(jobject, rive::StrokeCap);

    static void SetBlendMode(jobject, rive::BlendMode);

    static void SetShader(jobject, jobject);

    static void SetPaintAlpha(jobject, int);
};

class CanvasRenderImage : public rive::RenderImage
{
private:
    jobject m_ktBitmap = nullptr;
    jobject m_ktPaint = nullptr;

    static jobject CreateKtBitmapFrom(JNIEnv* env, rive::Span<const uint8_t>& encodedBytes)
    {
        jbyteArray byteArray = env->NewByteArray(encodedBytes.size());
        if (byteArray == nullptr)
        {
            LOGE("CreateKtBitmapFrom() - NewByteArray() failed.");
            return nullptr;
        }

        env->SetByteArrayRegion(byteArray,
                                0,
                                encodedBytes.size(),
                                reinterpret_cast<const jbyte*>(encodedBytes.data()));

        jclass bitmapFactoryClass = GetAndroidBitmapFactoryClass();
        jmethodID decodeByteArrayMethodID = GetDecodeByteArrayStaticMethodId();

        jobject bitmap = env->CallStaticObjectMethod(bitmapFactoryClass,
                                                     decodeByteArrayMethodID,
                                                     byteArray,
                                                     0,
                                                     SizeTTOInt(encodedBytes.size()));
        env->DeleteLocalRef(byteArray);
        env->DeleteLocalRef(bitmapFactoryClass);
        if (bitmap == nullptr)
        {
            LOGE("CreateKtBitmapFrom() - decodeByteArray() failed.");
            return nullptr;
        }
        return bitmap;
    }

public:
    CanvasRenderImage(rive::Span<const uint8_t> encodedBytes);

    ~CanvasRenderImage();

    jobject ktBitmap() const { return m_ktBitmap; }

    jobject ktPaint() const { return m_ktPaint; }

    static jobject CreateKtBitmapShader(jobject ktBitmap)
    {
        JNIEnv* env = GetJNIEnv();
        jclass bitmapShaderClass = GetBitmapShaderClass();
        jclass tileModeClass = GetTileModeClass();
        jobject clampEnum = env->GetStaticObjectField(tileModeClass, GetClampId());
        jobject ktShader = env->NewObject(bitmapShaderClass,
                                          GetBitmapShaderConstructor(),
                                          ktBitmap,
                                          clampEnum,
                                          clampEnum);
        env->DeleteLocalRef(tileModeClass);
        env->DeleteLocalRef(bitmapShaderClass);
        return ktShader;
    }
};
} // namespace rive_android
#endif // RIVE_ANDROID_CANVAS_RENDER_OBJECTS_HPP
