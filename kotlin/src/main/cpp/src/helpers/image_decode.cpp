#include "helpers/image_decode.hpp"

#include "helpers/android_factories.hpp"
#include "helpers/canvas_render_objects.hpp"
#include "helpers/general.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/jni_resource.hpp"
#include "jni_refs.hpp"

using namespace rive;

namespace rive_android
{
const uint32_t LSB_MASK = 0xFFu;

rive::rcp<rive::RenderImage> renderImageFromAndroidDecode(
    Span<const uint8_t> encodedBytes,
    bool isPremultiplied)
{
    auto env = GetJNIEnv();

    auto imageDecoderClass =
        FindClass(env, "app/rive/runtime/kotlin/core/ImageDecoder");

    auto decodeToBitmap = env->GetStaticMethodID(imageDecoderClass.get(),
                                                 "decodeToBitmap",
                                                 "([B)[I");

    auto encoded = env->NewByteArray(SizeTTOInt(encodedBytes.size()));
    if (!encoded)
    {
        LOGE("Failed to allocate NewByteArray");
        return nullptr;
    }

    env->SetByteArrayRegion(encoded,
                            0,
                            SizeTTOInt(encodedBytes.size()),
                            (jbyte*)encodedBytes.data());
    auto jPixels = (jintArray)JNIExceptionHandler::CallStaticObjectMethod(
        env,
        imageDecoderClass.get(),
        decodeToBitmap,
        encoded);
    env->DeleteLocalRef(encoded); // No longer need encoded

    if (jPixels == nullptr)
    {
        LOGE("ImageDecoder.decodeToBitmap returned null");
        return nullptr;
    }

    // At this point, we have the decoded results. Now convert into premul RGBA
    // bytes and wrap in an AndroidImage.

    jsize arrayCount = env->GetArrayLength(jPixels);
    if (arrayCount < 2)
    {
        LOGE("Bad array length (unexpected)");
        env->DeleteLocalRef(jPixels);
        return nullptr;
    }

    auto rawPixels = env->GetIntArrayElements(jPixels, nullptr);
    const auto rawWidth = static_cast<uint32_t>(rawPixels[0]);
    const auto rawHeight = static_cast<uint32_t>(rawPixels[1]);
    const size_t pixelCount = static_cast<size_t>(rawWidth) * rawHeight;
    if (pixelCount == 0)
    {
        LOGE("Unsupported empty image (zero dimension)");
        env->ReleaseIntArrayElements(jPixels, rawPixels, JNI_ABORT);
        env->DeleteLocalRef(jPixels);
        return nullptr;
    }
    if (static_cast<size_t>(arrayCount) < 2u + pixelCount)
    {
        LOGE("Not enough elements in pixel array");
        env->ReleaseIntArrayElements(jPixels, rawPixels, JNI_ABORT);
        env->DeleteLocalRef(jPixels);
        return nullptr;
    }

    std::unique_ptr<uint8_t[]> out(new uint8_t[pixelCount * 4]);
    auto* bytes = out.get();
    for (size_t i = 0; i < pixelCount; ++i)
    {
        auto p = static_cast<uint32_t>(rawPixels[2 + i]);
        uint32_t a = (p >> 24) & LSB_MASK;
        uint32_t r = (p >> 16) & LSB_MASK;
        uint32_t g = (p >> 8) & LSB_MASK;
        uint32_t b = (p >> 0) & LSB_MASK;
        if (!isPremultiplied)
        {
            r = premultiply(r, a);
            g = premultiply(g, a);
            b = premultiply(b, a);
        }
        bytes[0] = static_cast<uint8_t>(r);
        bytes[1] = static_cast<uint8_t>(g);
        bytes[2] = static_cast<uint8_t>(b);
        bytes[3] = static_cast<uint8_t>(a);
        bytes += 4;
    }
    env->ReleaseIntArrayElements(jPixels, rawPixels, JNI_ABORT);
    env->DeleteLocalRef(jPixels);

    return make_rcp<AndroidImage>(static_cast<int>(rawWidth),
                                  static_cast<int>(rawHeight),
                                  std::move(out));
}

rive::rcp<rive::RenderImage> renderImageFromRGBABytesRive(
    uint32_t width,
    uint32_t height,
    const uint8_t* pixelBytes,
    bool isPremultiplied)
{
    if (width == 0 || height == 0 || pixelBytes == nullptr)
    {
        LOGE("renderImageFromRGBABytesRive() - Invalid args.");
        return nullptr;
    }

    const auto pixelCount = static_cast<size_t>(width) * height;
    std::unique_ptr<uint8_t[]> out(new uint8_t[pixelCount * 4]);
    const auto* src = pixelBytes;
    auto* dst = out.get();
    for (auto i = 0; i < pixelCount; ++i)
    {
        uint32_t r = src[0];
        uint32_t g = src[1];
        uint32_t b = src[2];
        uint32_t a = src[3];
        if (!isPremultiplied)
        {
            r = premultiply(r, a);
            g = premultiply(g, a);
            b = premultiply(b, a);
        }
        dst[0] = static_cast<uint8_t>(r);
        dst[1] = static_cast<uint8_t>(g);
        dst[2] = static_cast<uint8_t>(b);
        dst[3] = static_cast<uint8_t>(a);
        src += 4;
        dst += 4;
    }
    return make_rcp<AndroidImage>(static_cast<int>(width),
                                  static_cast<int>(height),
                                  std::move(out));
}

rive::rcp<rive::RenderImage> renderImageFromRGBABytesCanvas(
    uint32_t width,
    uint32_t height,
    const uint8_t* pixelBytes,
    bool isPremultiplied)
{
    if (width == 0 || height == 0 || pixelBytes == nullptr)
    {
        LOGE("renderImageFromRGBABytesCanvas() - Invalid args.");
        return nullptr;
    }
    const auto pixelCount = static_cast<size_t>(width) * height;
    auto* env = GetJNIEnv();
    auto jConfig = env->GetStaticObjectField(GetAndroidBitmapConfigClass(),
                                             GetARGB8888Field());
    auto jBitmap = JNIExceptionHandler::CallStaticObjectMethod(
        env,
        GetAndroidBitmapClass(),
        GetCreateBitmapStaticMethodId(),
        static_cast<jint>(width),
        static_cast<jint>(height),
        jConfig);
    if (jBitmap == nullptr)
    {
        LOGE("renderImageFromRGBABytesCanvas() - Bitmap.createBitmap(...) "
             "failed.");
        return nullptr;
    }
    auto jCount = static_cast<jsize>(pixelCount);
    auto pixels = env->NewIntArray(jCount);
    if (pixels == nullptr)
    {
        env->DeleteLocalRef(jBitmap);
        LOGE("renderImageFromRGBABytesCanvas() - NewIntArray failed.");
        return nullptr;
    }
    auto* out = env->GetIntArrayElements(pixels, nullptr);
    const auto* src = pixelBytes;
    for (size_t i = 0; i < pixelCount; ++i)
    {
        uint32_t r = src[0];
        uint32_t g = src[1];
        uint32_t b = src[2];
        uint32_t a = src[3];
        if (isPremultiplied && a != 255)
        {
            r = unpremultiply(r, a);
            g = unpremultiply(g, a);
            b = unpremultiply(b, a);
        }
        out[i] = static_cast<jint>((a << 24) | (r << 16) | (g << 8) | (b));
        src += 4;
    }
    env->ReleaseIntArrayElements(pixels, out, 0);
    JNIExceptionHandler::CallVoidMethod(env,
                                        jBitmap,
                                        GetBitmapSetPixelsMethodId(),
                                        pixels,
                                        0,
                                        static_cast<jint>(width),
                                        0,
                                        0,
                                        static_cast<jint>(width),
                                        static_cast<jint>(height));
    auto renderImage = make_rcp<CanvasRenderImage>(jBitmap);
    env->DeleteLocalRef(pixels);
    env->DeleteLocalRef(jBitmap);
    return renderImage;
}

rive::rcp<rive::RenderImage> renderImageFromARGBIntsRive(uint32_t width,
                                                         uint32_t height,
                                                         const uint32_t* pixels,
                                                         bool isPremultiplied)
{
    if (width == 0 || height == 0 || pixels == nullptr)
    {
        LOGE("renderImageFromARGBIntsRive() - Invalid args.");
        return nullptr;
    }
    const auto pixelCount = static_cast<size_t>(width) * height;
    std::unique_ptr<uint8_t[]> out(new uint8_t[pixelCount * 4]);
    for (size_t i = 0; i < pixelCount; ++i)
    {
        uint32_t c = pixels[i];
        uint32_t a = (c >> 24) & LSB_MASK;
        uint32_t r = (c >> 16) & LSB_MASK;
        uint32_t g = (c >> 8) & LSB_MASK;
        uint32_t b = (c >> 0) & LSB_MASK;
        if (!isPremultiplied)
        {
            r = premultiply(r, a);
            g = premultiply(g, a);
            b = premultiply(b, a);
        }
        out[i * 4 + 0] = static_cast<uint8_t>(r);
        out[i * 4 + 1] = static_cast<uint8_t>(g);
        out[i * 4 + 2] = static_cast<uint8_t>(b);
        out[i * 4 + 3] = static_cast<uint8_t>(a);
    }
    return make_rcp<AndroidImage>(static_cast<int>(width),
                                  static_cast<int>(height),
                                  std::move(out));
}

rive::rcp<rive::RenderImage> renderImageFromARGBIntsCanvas(
    uint32_t width,
    uint32_t height,
    const uint32_t* pixels,
    bool isPremultiplied)
{
    if (width == 0 || height == 0 || pixels == nullptr)
    {
        LOGE("renderImageFromARGBIntsCanvas() - Invalid args.");
        return nullptr;
    }
    const auto pixelCount = static_cast<size_t>(width) * height;
    auto* env = GetJNIEnv();
    auto jConfig = env->GetStaticObjectField(GetAndroidBitmapConfigClass(),
                                             GetARGB8888Field());
    auto jBitmap = JNIExceptionHandler::CallStaticObjectMethod(
        env,
        GetAndroidBitmapClass(),
        GetCreateBitmapStaticMethodId(),
        static_cast<jint>(width),
        static_cast<jint>(height),
        jConfig);
    if (jBitmap == nullptr)
    {
        LOGE("renderImageFromARGBIntsCanvas() - Bitmap.createBitmap(...) "
             "failed.");
        return nullptr;
    }
    auto count = static_cast<jsize>(pixelCount);
    auto dstPixels = env->NewIntArray(count);
    if (dstPixels == nullptr)
    {
        env->DeleteLocalRef(jBitmap);
        LOGE("renderImageFromARGBIntsCanvas() - NewIntArray failed.");
        return nullptr;
    }
    if (!isPremultiplied)
    {
        env->SetIntArrayRegion(dstPixels,
                               0,
                               count,
                               reinterpret_cast<const jint*>(pixels));
    }
    else
    {
        std::unique_ptr<jint[]> tmp(new jint[pixelCount]);
        for (size_t i = 0; i < pixelCount; ++i)
        {
            uint32_t c = pixels[i];
            uint32_t a = (c >> 24) & LSB_MASK;
            uint32_t r = (c >> 16) & LSB_MASK;
            uint32_t g = (c >> 8) & LSB_MASK;
            uint32_t b = (c >> 0) & LSB_MASK;
            if (a != 255)
            {
                r = unpremultiply(r, a);
                g = unpremultiply(g, a);
                b = unpremultiply(b, a);
            }
            tmp[i] = static_cast<jint>((a << 24) | (r << 16) | (g << 8) | (b));
        }
        env->SetIntArrayRegion(dstPixels, 0, count, tmp.get());
    }
    JNIExceptionHandler::CallVoidMethod(env,
                                        jBitmap,
                                        GetBitmapSetPixelsMethodId(),
                                        dstPixels,
                                        0,
                                        static_cast<jint>(width),
                                        0,
                                        0,
                                        static_cast<jint>(width),
                                        static_cast<jint>(height));
    auto renderImage = make_rcp<CanvasRenderImage>(jBitmap);
    env->DeleteLocalRef(dstPixels);
    env->DeleteLocalRef(jBitmap);
    return renderImage;
}

rive::rcp<rive::RenderImage> renderImageFromBitmapRive(jobject jBitmap,
                                                       bool isPremultiplied)
{
    if (jBitmap == nullptr)
    {
        LOGE("renderImageFromBitmapRive() - Bitmap was null.");
        return nullptr;
    }
    auto* env = GetJNIEnv();
    AndroidBitmapInfo info;
    const uint32_t* srcPixels = nullptr;
    if (!lockBitmapRGBA8888(env, jBitmap, &info, &srcPixels))
    {
        LOGE("renderImageFromBitmapRive() - Failed to lock srcPixels.");
        return nullptr;
    }

    const auto width = info.width;
    const auto height = info.height;

    // Pack bitmap data in RGBA8888 format into a contiguous RGBA buffer
    const size_t byteCount = static_cast<size_t>(width) * height * 4;
    std::unique_ptr<uint8_t[]> dstBytes(new uint8_t[byteCount]);

    const auto srcStrideBytes = static_cast<size_t>(info.stride);
    const auto rowBytes = static_cast<size_t>(width) * 4;

    // Reinterpret RGBA ints as bytes
    const auto* srcBytes = reinterpret_cast<const uint8_t*>(srcPixels);

    // Contiguous case: direct memcpy
    if (srcStrideBytes == rowBytes)
    {
        memcpy(dstBytes.get(), srcBytes, rowBytes * height);
    }
    // Strided case: copy row by row
    else
    {
        for (uint32_t y = 0; y < height; ++y)
        {
            const uint8_t* srcRow = srcBytes + y * srcStrideBytes;
            uint8_t* dstRow =
                dstBytes.get() + static_cast<size_t>(y) * rowBytes;
            memcpy(dstRow, srcRow, rowBytes);
        }
    }

    // Always unlock srcPixels
    AndroidBitmap_unlockPixels(env, jBitmap);

    // The packed buffer is RGBA. Route through the RGBA path that handles
    // isPremultiplied/straight alpha
    return renderImageFromRGBABytesRive(width,
                                        height,
                                        dstBytes.get(),
                                        isPremultiplied);
}

rive::rcp<rive::RenderImage> renderImageFromBitmapCanvas(jobject jBitmap)
{
    if (jBitmap == nullptr)
    {
        LOGE("renderImageFromBitmapCanvas() - Bitmap was null.");
        return nullptr;
    }
    return make_rcp<CanvasRenderImage>(jBitmap);
}

uint32_t premultiply(uint8_t c, uint8_t a)
{
    switch (a)
    {
        case 0:
            return 0;
        case 255:
            return c;
        default:
            // Slightly faster than (c * a + 127) / 255
            return (c * a + 128) * 257 >> 16;
    }
}

uint32_t unpremultiply(uint8_t c, uint8_t a)
{
    if (a == 0)
        return 0;
    auto out = (c * 255u + (a / 2u)) / a;
    return out > 255u ? 255u : out;
}

bool lockBitmapRGBA8888(JNIEnv* env,
                        jobject jBitmap,
                        AndroidBitmapInfo* info,
                        const uint32_t** pixels)
{
    if (AndroidBitmap_getInfo(env, jBitmap, info) !=
        ANDROID_BITMAP_RESULT_SUCCESS)
    {
        LOGE("lockBitmapRGBA8888() - AndroidBitmap_getInfo failed.");
        return false;
    }
    if (info->format != ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        LOGE("lockBitmapRGBA8888() - Unexpected bitmap format.");
        return false;
    }
    if (info->width == 0 || info->height == 0)
    {
        LOGE("lockBitmapRGBA8888() - Invalid dimensions.");
        return false;
    }
    void* lockedPixelsPtr = nullptr;
    auto lockResult = AndroidBitmap_lockPixels(env, jBitmap, &lockedPixelsPtr);
    if (lockResult != ANDROID_BITMAP_RESULT_SUCCESS ||
        lockedPixelsPtr == nullptr)
    {
        if (lockResult == ANDROID_BITMAP_RESULT_BAD_PARAMETER)
        {
            LOGE("lockBitmapRGBA8888() - Failed to lock pixes: bad "
                 "parameter.");
        }
        else if (lockResult == ANDROID_BITMAP_RESULT_JNI_EXCEPTION)
        {
            LOGE("lockBitmapRGBA8888() - Failed to lock pixes: JNI exception "
                 "occurred.");
        }
        else if (lockResult == ANDROID_BITMAP_RESULT_ALLOCATION_FAILED)
        {
            LOGE("lockBitmapRGBA8888() - Failed to lock pixes: allocation "
                 "failed.");
        }
        else
        {
            LOGE("lockBitmapRGBA8888() - Failed to lock pixes: unknown "
                 "error: %d",
                 lockResult);
        }

        return false;
    }
    *pixels = reinterpret_cast<uint32_t*>(lockedPixelsPtr);
    return true;
}

} // namespace rive_android
