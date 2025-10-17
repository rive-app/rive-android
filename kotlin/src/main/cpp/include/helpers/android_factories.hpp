#pragma once

#include "helpers/general.hpp"
#include "helpers/worker_ref.hpp"

#include "rive/refcnt.hpp"
#include "rive/renderer/rive_render_factory.hpp"
#include "rive/renderer/rive_render_image.hpp"

namespace rive_android
{
// Forward declare template specialization.
template <typename AssetType>
rive::rcp<AssetType> decode(rive::Span<const uint8_t>, RendererType);

template <typename AssetType>
rive::rcp<AssetType> decodeAsset(JNIEnv* env,
                                 jbyteArray byteArray,
                                 jint rendererTypeIdx)
{
    jsize count = env->GetArrayLength(byteArray);
    jbyte* buffer = env->GetByteArrayElements(byteArray, nullptr);
    rive::Span<const uint8_t> data(reinterpret_cast<const uint8_t*>(buffer),
                                   count);

    auto rendererType = static_cast<RendererType>(rendererTypeIdx);
    rive::rcp<AssetType> asset = decode<AssetType>(data, rendererType);

    env->ReleaseByteArrayElements(byteArray, buffer, JNI_ABORT);

    return asset;
}

template <typename AssetType> void releaseAsset(jlong address)
{
    auto* asset = reinterpret_cast<AssetType*>(address);
    rive::safe_unref(asset);
}

class AndroidRiveRenderFactory : public rive::RiveRenderFactory
{
public:
    rive::rcp<rive::RenderBuffer> makeRenderBuffer(rive::RenderBufferType,
                                                   rive::RenderBufferFlags,
                                                   size_t) override;

    rive::rcp<rive::RenderImage> decodeImage(
        rive::Span<const uint8_t>) override;
};

class AndroidImage : public rive::RiveRenderImage
{
public:
    AndroidImage(int width,
                 int height,
                 std::unique_ptr<const uint8_t[]> imageDataRGBAPtr);

    ~AndroidImage() override;

private:
    const rive::rcp<RefWorker> m_glWorker;
    RefWorker::WorkID m_textureCreationWorkID;
};

class AndroidCanvasFactory : public rive::Factory
{
public:
    rive::rcp<rive::RenderBuffer> makeRenderBuffer(
        rive::RenderBufferType type,
        rive::RenderBufferFlags flags,
        size_t sizeInBytes) override;

    rive::rcp<rive::RenderImage> decodeImage(
        rive::Span<const uint8_t> encodedBytes) override;

    rive::rcp<rive::RenderShader> makeLinearGradient(
        float sx,
        float sy,
        float ex,
        float ey,
        const rive::ColorInt colors[], // [count]
        const float stops[],           // [count]
        size_t count) override;

    rive::rcp<rive::RenderShader> makeRadialGradient(
        float cx,
        float cy,
        float radius,
        const rive::ColorInt colors[], // [count]
        const float stops[],           // [count]
        size_t count) override;

    rive::rcp<rive::RenderPath> makeRenderPath(
        rive::RawPath& rawPath,
        rive::FillRule fillRule) override;

    rive::rcp<rive::RenderPath> makeEmptyRenderPath() override;

    rive::rcp<rive::RenderPaint> makeRenderPaint() override;
};
} // namespace rive_android
