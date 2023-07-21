//
// Created by Umberto Sonnino on 7/18/23.
//
#ifndef RIVE_ANDROID_ANDROID_SKIA_FACTORY_HPP
#define RIVE_ANDROID_ANDROID_SKIA_FACTORY_HPP

#include <vector>

#include "skia_factory.hpp"
#include "helpers/general.hpp"

class AndroidSkiaFactory : public rive::SkiaFactory
{
public:
    std::vector<uint8_t> platformDecode(rive::Span<const uint8_t> span,
                                        rive::SkiaFactory::ImageInfo* info) override
    {
        auto env = rive_android::GetJNIEnv();
        std::vector<uint8_t> pixels;

        jclass cls = env->FindClass("app/rive/runtime/kotlin/core/Decoder");
        if (!cls)
        {
            LOGE("can't find class 'app/rive/runtime/kotlin/core/Decoder'");
            return pixels;
        }

        jmethodID method = env->GetStaticMethodID(cls, "decodeToPixels", "([B)[I");
        if (!method)
        {
            LOGE("can't find static method decodeToPixels");
            return pixels;
        }

        jbyteArray encoded = env->NewByteArray(rive_android::SizeTTOInt(span.size()));
        if (!encoded)
        {
            LOGE("failed to allocate NewByteArray");
            return pixels;
        }

        env->SetByteArrayRegion(encoded,
                                0,
                                rive_android::SizeTTOInt(span.size()),
                                (jbyte*)span.data());
        auto jpixels = (jintArray)env->CallStaticObjectMethod(cls, method, encoded);
        env->DeleteLocalRef(encoded); // no longer need encoded

        // At ths point, we have the decode results. Now we just need to convert
        // it into the form we need (ImageInfo + premul pixels)

        size_t arrayCount = env->GetArrayLength(jpixels);
        if (arrayCount < 2)
        {
            LOGE("bad array length (unexpected)");
            return pixels;
        }

        int* rawPixels = env->GetIntArrayElements(jpixels, nullptr);
        const uint32_t width = rawPixels[0];
        const uint32_t height = rawPixels[1];
        const size_t pixelCount = (size_t)width * height;
        if (pixelCount == 0)
        {
            LOGE("don't support empty images (zero dimension)");
            return pixels;
        }
        if (2 + pixelCount < arrayCount)
        {
            LOGE("not enough elements in pixel array");
            return pixels;
        }

        auto div255 = [](unsigned value) { return (value + 128) * 257 >> 16; };

        pixels.resize(pixelCount * 4);
        uint8_t* bytes = pixels.data();
        bool isOpaque = true;
        for (size_t i = 0; i < pixelCount; ++i)
        {
            uint32_t p = rawPixels[2 + i];
            unsigned a = (p >> 24) & 0xFF;
            unsigned r = (p >> 16) & 0xFF;
            unsigned g = (p >> 8) & 0xFF;
            unsigned b = (p >> 0) & 0xFF;
            // convert to premul as needed
            if (a != 255)
            {
                r = div255(r * a);
                g = div255(g * a);
                b = div255(b * a);
                isOpaque = false;
            }
            bytes[0] = r;
            bytes[1] = g;
            bytes[2] = b;
            bytes[3] = a;
            bytes += 4;
        }
        env->ReleaseIntArrayElements(jpixels, rawPixels, 0);

        info->rowBytes = width * 4; // we're snug
        info->width = width;
        info->height = height;
        info->colorType = ColorType::rgba;
        info->alphaType = isOpaque ? AlphaType::opaque : AlphaType::premul;
        return pixels;
    }
};

#endif // RIVE_ANDROID_ANDROID_SKIA_FACTORY_HPP
