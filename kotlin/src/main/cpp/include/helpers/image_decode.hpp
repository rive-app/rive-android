#pragma once

#include "rive/refcnt.hpp"
#include "rive/renderer.hpp"
#include "rive/span.hpp"

#include <android/bitmap.h>
#include <cstdint>
#include <jni.h>
#include <vector>

namespace rive_android
{
/** Decode path: Use Android BitmapFactory through JNI to decode into RGBA bytes
 */
rive::rcp<rive::RenderImage> renderImageFromAndroidDecode(
    rive::Span<const uint8_t> encodedBytes,
    bool isPremultiplied);

/** Rive (GL) path: From RGBA bytes -> AndroidImage */
rive::rcp<rive::RenderImage> renderImageFromRGBABytesRive(
    uint32_t width,
    uint32_t height,
    const uint8_t* pixelBytes,
    bool isPremultiplied);

/** Canvas path: from RGBA bytes -> ARGB ints -> Bitmap::setPixels ->
 * CanvasRenderImage */
rive::rcp<rive::RenderImage> renderImageFromRGBABytesCanvas(
    uint32_t width,
    uint32_t height,
    const uint8_t* pixelBytes,
    bool isPremultiplied);

/** Rive (GL) path: from ARGB ints -> RGBA bytes -> AndroidImage */
rive::rcp<rive::RenderImage> renderImageFromARGBIntsRive(uint32_t width,
                                                         uint32_t height,
                                                         const uint32_t* pixels,
                                                         bool isPremultiplied);

/** Canvas path: from ARGB ints -> Bitmap::setPixels -> CanvasRenderImage */
rive::rcp<rive::RenderImage> renderImageFromARGBIntsCanvas(
    uint32_t width,
    uint32_t height,
    const uint32_t* pixels,
    bool isPremultiplied);

/** Rive (GL) path: Android Bitmap -> internal buffer -> RGBA bytes ->
 * AndroidImage */
rive::rcp<rive::RenderImage> renderImageFromBitmapRive(jobject jBitmap,
                                                       bool isPremultiplied);

/** Canvas path: Android Bitmap -> wrap -> CanvasRenderImage */
rive::rcp<rive::RenderImage> renderImageFromBitmapCanvas(jobject jBitmap);

/** Premultiply/unpremultiply helpers for a given channel */
uint32_t premultiply(uint8_t c, uint8_t a);
uint32_t unpremultiply(uint8_t c, uint8_t a);

/** Lock an Android Bitmap to access its buffer and ensure it's RGBA_8888
 * format. */
bool lockBitmapRGBA8888(JNIEnv* env,
                        jobject jBitmap,
                        AndroidBitmapInfo* info,
                        const uint32_t** pixels);

} // namespace rive_android
