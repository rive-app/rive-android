/*
 * Copyright 2023 Rive
 */
#ifndef _RIVE_ANDROID_CANVAS_RENDERER_HPP_
#define _RIVE_ANDROID_CANVAS_RENDERER_HPP_

#include <jni.h>
#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "rive/renderer.hpp"

namespace rive_android
{
class CanvasRenderer : public rive::Renderer
{
protected:
    jobject m_ktCanvas = nullptr;
    int m_width = -1;
    int m_height = -1;

private:
    static jobject GetCanvas(jobject ktSurface)
    {
        return GetJNIEnv()->CallObjectMethod(ktSurface,
                                             GetSurfaceLockCanvasMethodId(),
                                             nullptr);
    }

    static void Clear(jobject ktCanvas)
    {
        JNIEnv* env = GetJNIEnv();

        jclass porterDuffModeClass = GetPorterDuffClass();
        jobject clearMode =
            env->GetStaticObjectField(porterDuffModeClass, GetPdClear());
        env->DeleteLocalRef(porterDuffModeClass);
        if (clearMode == nullptr)
        {
            LOGE("Failed to get PorterDuff.Mode.CLEAR.");
            return;
        }

        // canvas.drawColor(Color.TRANSPARENT, PortDuff.Mode.Clear)
        JNIExceptionHandler::CallVoidMethod(env,
                                            ktCanvas,
                                            GetCanvasDrawColorMethodId(),
                                            0x0 /* Color.TRANSPARENT */,
                                            clearMode);
    }

public:
    ~CanvasRenderer() { assert(m_ktCanvas == nullptr); }
    void save() override;
    void restore() override;
    void transform(const rive::Mat2D& transform) override;
    void clipPath(rive::RenderPath* path) override;
    void drawPath(rive::RenderPath* path, rive::RenderPaint* paint) override;
    void drawImage(const rive::RenderImage*,
                   rive::BlendMode,
                   float opacity) override;
    void drawImageMesh(const rive::RenderImage*,
                       rive::rcp<rive::RenderBuffer> vertices_f32,
                       rive::rcp<rive::RenderBuffer> uvCoords_f32,
                       rive::rcp<rive::RenderBuffer> indices_u16,
                       uint32_t vertexCount,
                       uint32_t indexCount,
                       rive::BlendMode,
                       float opacity) override;

    int width() const { return m_width; }
    int height() const { return m_height; }

    void bindCanvas(jobject ktSurface)
    {
        // Old canvas needs to be unbound as it might not be valid anymore.
        assert(m_ktCanvas == nullptr);
        JNIEnv* env = GetJNIEnv();
        m_ktCanvas = env->NewGlobalRef(GetCanvas(ktSurface));
        m_width = JNIExceptionHandler::CallIntMethod(env,
                                                     m_ktCanvas,
                                                     GetCanvasWidthMethodId());
        m_height =
            JNIExceptionHandler::CallIntMethod(env,
                                               m_ktCanvas,
                                               GetCanvasHeightMethodId());
        Clear(m_ktCanvas);
    }

    void unlockAndPost(jobject ktSurface)
    {
        JNIEnv* env = GetJNIEnv();
        JNIExceptionHandler::CallVoidMethod(
            env,
            ktSurface,
            GetSurfaceUnlockCanvasAndPostMethodId(),
            m_ktCanvas);

        m_width = -1;
        m_height = -1;
        env->DeleteGlobalRef(m_ktCanvas);
        m_ktCanvas = nullptr;
    }
};
} // namespace rive_android
#endif // _RIVE_ANDROID_CANVAS_RENDERER_HPP_
