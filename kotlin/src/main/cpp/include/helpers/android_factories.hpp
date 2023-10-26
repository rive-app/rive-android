//
// Created by Umberto Sonnino on 7/18/23.
//
#ifndef RIVE_ANDROID_ANDROID_SKIA_FACTORY_HPP
#define RIVE_ANDROID_ANDROID_SKIA_FACTORY_HPP

#include "rive/pls/pls_factory.hpp"
#include "skia_factory.hpp"
#include <vector>

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

#endif // RIVE_ANDROID_ANDROID_SKIA_FACTORY_HPP
