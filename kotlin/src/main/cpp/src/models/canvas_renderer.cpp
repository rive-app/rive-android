/*
 * Copyright 2023 Rive
 */
#include "jni_refs.hpp"
#include "models/canvas_renderer.hpp"
#include "helpers/canvas_render_objects.hpp"
#include "utils/factory_utils.hpp"

namespace rive_android
{
void CanvasRenderer::save()
{
    // bind m_ktCanvas before calling these methods.
    assert(m_ktCanvas != nullptr);
    GetJNIEnv()->CallIntMethod(m_ktCanvas, GetCanvasSaveMethodId());
}
void CanvasRenderer::restore()
{
    // bind m_ktCanvas before calling these methods.
    assert(m_ktCanvas != nullptr);
    GetJNIEnv()->CallVoidMethod(m_ktCanvas, GetCanvasRestoreMethodId());
}
void CanvasRenderer::transform(const rive::Mat2D& transform)
{
    // bind m_ktCanvas before calling these methods.
    assert(m_ktCanvas != nullptr);

    JNIEnv* env = GetJNIEnv();
    jclass matrixClass = GetMatrixClass();
    jobject matrix = env->NewObject(matrixClass, GetMatrixInitMethodId());

    float squareMatrix[9] = {transform.xx(),
                             transform.yx(),
                             transform.tx(),
                             transform.xy(),
                             transform.yy(),
                             transform.ty(),
                             0,
                             0,
                             1};
    jfloatArray matrixArray = env->NewFloatArray(9);
    env->SetFloatArrayRegion(matrixArray, 0, 9, squareMatrix);
    env->CallVoidMethod(matrix, GetMatrixSetValuesMethodId(), matrixArray);

    env->CallVoidMethod(m_ktCanvas, GetCanvasConcatMatrixMethodId(), matrix);

    env->DeleteLocalRef(matrixClass);
    env->DeleteLocalRef(matrixArray);
    env->DeleteLocalRef(matrix);
}
void CanvasRenderer::clipPath(rive::RenderPath* path)
{
    // bind m_ktCanvas before calling these methods.
    assert(m_ktCanvas != nullptr);
    CanvasRenderPath* canvasPath = static_cast<CanvasRenderPath*>(path);
    GetJNIEnv()->CallBooleanMethod(m_ktCanvas,
                                   GetCanvasClipPathMethodId(),
                                   canvasPath->ktPath());
}
void CanvasRenderer::drawPath(rive::RenderPath* path, rive::RenderPaint* paint)
{
    // bind m_ktCanvas before calling these methods.
    assert(m_ktCanvas != nullptr);
    CanvasRenderPath* canvasPath = static_cast<CanvasRenderPath*>(path);
    CanvasRenderPaint* canvasPaint = static_cast<CanvasRenderPaint*>(paint);
    GetJNIEnv()->CallVoidMethod(m_ktCanvas,
                                GetCanvasDrawPathMethodId(),
                                canvasPath->ktPath(),
                                canvasPaint->ktPaint());
}
void CanvasRenderer::drawImage(const rive::RenderImage* image,
                               rive::BlendMode blendMode,
                               float opacity)
{
    // bind m_ktCanvas before calling these methods.
    assert(m_ktCanvas != nullptr);

    const CanvasRenderImage* canvasImage =
        static_cast<const CanvasRenderImage*>(image);
    jobject ktPaint = canvasImage->ktPaint();
    // Opacity is [0.0f..1.0f] while setAlpha() needs [0..255]
    CanvasRenderPaint::SetPaintAlpha(ktPaint, static_cast<int>(opacity * 255));
    CanvasRenderPaint::SetBlendMode(ktPaint, blendMode);
    GetJNIEnv()->CallVoidMethod(ktPaint, GetSetAntiAliasMethodId(), JNI_TRUE);

    GetJNIEnv()->CallVoidMethod(m_ktCanvas,
                                GetCanvasDrawBitmapMethodId(),
                                canvasImage->ktBitmap(),
                                0.0f,
                                0.0f,
                                ktPaint);
}

void CanvasRenderer::drawImageMesh(const rive::RenderImage* image,
                                   rive::rcp<rive::RenderBuffer> vertices_f32,
                                   rive::rcp<rive::RenderBuffer> uvCoords_f32,
                                   rive::rcp<rive::RenderBuffer> indices_u16,
                                   uint32_t vertexCount,
                                   uint32_t indexCount,
                                   rive::BlendMode blendMode,
                                   float opacity)
{
    // bind m_ktCanvas before calling these methods.
    assert(m_ktCanvas != nullptr);
    const CanvasRenderImage* canvasImage =
        static_cast<const CanvasRenderImage*>(image);
    jobject ktPaint = canvasImage->ktPaint();
    // Opacity is [0.0f..1.0f] while setAlpha() needs [0..255]
    CanvasRenderPaint::SetPaintAlpha(ktPaint, static_cast<int>(opacity * 255));
    CanvasRenderPaint::SetBlendMode(ktPaint, blendMode);

    JNIEnv* env = GetJNIEnv();
    env->CallVoidMethod(ktPaint, GetSetAntiAliasMethodId(), JNI_TRUE);

    jobject ktShader =
        CanvasRenderImage::CreateKtBitmapShader(canvasImage->ktBitmap());
    CanvasRenderPaint::SetShader(ktPaint, ktShader);

    jclass vertexModeClass = GetAndroidCanvasVertexModeClass();
    jobject trianglesMode =
        env->GetStaticObjectField(vertexModeClass, GetVertexModeTrianglesId());
    env->DeleteLocalRef(vertexModeClass);

    /** Set up the vertices */
    const float* vertices =
        static_cast<rive::DataRenderBuffer*>(vertices_f32.get())->f32s();
    jfloatArray verticesArray = env->NewFloatArray(vertexCount * 2);
    env->SetFloatArrayRegion(verticesArray, 0, vertexCount * 2, vertices);

    /** Set up the uvs */
    const float* uvs =
        static_cast<rive::DataRenderBuffer*>(uvCoords_f32.get())->f32s();
    std::vector<float> scaledUVs(vertexCount * 2);
    for (int i = 0; i < vertexCount; i++)
    {
        // Need to manually scale UVs for canvas.drawVertices() to work.
        scaledUVs[i * 2] = uvs[i * 2] * image->width();
        scaledUVs[i * 2 + 1] = uvs[i * 2 + 1] * image->height();
    }
    jfloatArray uvsArray = env->NewFloatArray(vertexCount * 2);
    env->SetFloatArrayRegion(uvsArray, 0, vertexCount * 2, scaledUVs.data());

    /** Set up the indices */
    const uint16_t* indices =
        static_cast<rive::DataRenderBuffer*>(indices_u16.get())->u16s();
    jshortArray indicesArray = env->NewShortArray(indexCount);
    env->SetShortArrayRegion(indicesArray,
                             0,
                             indexCount,
                             reinterpret_cast<const jshort*>(indices));
    uint32_t* no_colors = nullptr;

    env->CallVoidMethod(m_ktCanvas,
                        GetCanvasDrawVerticesMethodId(),
                        trianglesMode,   // Canvas.VertexMode mode,
                        vertexCount * 2, // int vertexCount,
                        verticesArray,   // float[] verts,
                        0,               // int vertOffset,
                        uvsArray,        // float[] texs,
                        0,               // int texOffset,
                        no_colors,       // int[] colors,
                        0,               // int colorOffset,
                        indicesArray,    // short[] indices,
                        0,               // int indexOffset,
                        indexCount,      // int indexCount,
                        ktPaint          // Paint paint
    );

    env->DeleteLocalRef(verticesArray);
    env->DeleteLocalRef(uvsArray);
    env->DeleteLocalRef(indicesArray);
}
} // namespace rive_android
