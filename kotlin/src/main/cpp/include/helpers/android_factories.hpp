//
// Created by Umberto Sonnino on 7/18/23.
//
#ifndef RIVE_ANDROID_FACTORIES_HPP
#define RIVE_ANDROID_FACTORIES_HPP

#include "jni_refs.hpp"
#include "skia_factory.hpp"
#include "helpers/general.hpp"

#include "rive/pls/pls_factory.hpp"
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

class AndroidSkiaFactory : public rive::SkiaFactory
{
public:
    std::vector<uint8_t> platformDecode(rive::Span<const uint8_t> encodedBytes,
                                        rive::SkiaFactory::ImageInfo* info) override;
};

class AndroidPLSFactory : public rive::pls::PLSFactory
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
