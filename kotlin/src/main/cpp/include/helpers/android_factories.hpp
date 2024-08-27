//
// Created by Umberto Sonnino on 7/18/23.
//
#ifndef RIVE_ANDROID_FACTORIES_HPP
#define RIVE_ANDROID_FACTORIES_HPP

#include "jni_refs.hpp"
#include "skia_factory.hpp"
#include "helpers/general.hpp"

#include "rive/renderer/rive_render_factory.hpp"
#include "utils/factory_utils.hpp"
#include <vector>
#include <jni.h>

namespace rive_android
{
bool JNIDecodeImage(rive::Span<const uint8_t> encodedBytes,
                    bool premultiply,
                    uint32_t* width,
                    uint32_t* height,
                    std::vector<uint8_t>* pixels,
                    bool* isOpaque);

// Forward declare template specialization.
template <typename AssetType> rive::rcp<AssetType> decode(rive::Span<const uint8_t>, RendererType);

template <typename AssetType>
rive::rcp<AssetType> decodeAsset(JNIEnv* env, jbyteArray byteArray, jint rendererTypeIdx)
{
    jsize count = env->GetArrayLength(byteArray);
    jbyte* buffer = env->GetByteArrayElements(byteArray, nullptr);
    rive::Span<const uint8_t> data(reinterpret_cast<const uint8_t*>(buffer), count);

    RendererType rendererType = static_cast<RendererType>(rendererTypeIdx);
    rive::rcp<AssetType> asset = decode<AssetType>(data, rendererType);

    env->ReleaseByteArrayElements(byteArray, buffer, JNI_ABORT);

    return asset;
}

template <typename AssetType> void releaseAsset(jlong address)
{
    AssetType* asset = reinterpret_cast<AssetType*>(address);
    rive::safe_unref(asset);
}

class AndroidSkiaFactory : public rive::SkiaFactory
{
public:
    std::vector<uint8_t> platformDecode(rive::Span<const uint8_t> encodedBytes,
                                        rive::SkiaFactory::ImageInfo* info) override;
};

class AndroidRiveRenderFactory : public rive::gpu::RiveRenderFactory
{
public:
    rive::rcp<rive::RenderBuffer> makeRenderBuffer(rive::RenderBufferType,
                                                   rive::RenderBufferFlags,
                                                   size_t) override;

    rive::rcp<rive::RenderImage> decodeImage(rive::Span<const uint8_t>) override;
};
class AndroidCanvasFactory : public rive::Factory
{
public:
    rive::rcp<rive::RenderBuffer> makeRenderBuffer(rive::RenderBufferType type,
                                                   rive::RenderBufferFlags flags,
                                                   size_t sizeInBytes) override;

    rive::rcp<rive::RenderImage> decodeImage(rive::Span<const uint8_t> encodedBytes) override;

    rive::rcp<rive::RenderShader> makeLinearGradient(float sx,
                                                     float sy,
                                                     float ex,
                                                     float ey,
                                                     const rive::ColorInt colors[], // [count]
                                                     const float stops[],           // [count]
                                                     size_t count) override;

    rive::rcp<rive::RenderShader> makeRadialGradient(float cx,
                                                     float cy,
                                                     float radius,
                                                     const rive::ColorInt colors[], // [count]
                                                     const float stops[],           // [count]
                                                     size_t count) override;

    rive::rcp<rive::RenderPath> makeRenderPath(rive::RawPath& rawPath,
                                               rive::FillRule fillRule) override;

    rive::rcp<rive::RenderPath> makeEmptyRenderPath() override;

    rive::rcp<rive::RenderPaint> makeRenderPaint() override;
};
} // namespace rive_android
#endif // RIVE_ANDROID_FACTORIES_HPP
