#include "helpers/image_decode.hpp"

#include "helpers/android_factories.hpp"
#include "helpers/canvas_render_objects.hpp"
#include "helpers/conversions.hpp"
#include "helpers/general.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/jni_resource.hpp"
#include "jni_refs.hpp"

using namespace rive;

namespace rive_android
{
constexpr auto* TAG = "RiveLN/ImageDecode";
const uint32_t LSB_MASK = 0xFFu;

std::unique_ptr<uint8_t[]> decodeToPremultipliedRGBA(
    Span<const uint8_t> encodedBytes,
    uint32_t* width,
    uint32_t* height)
{
  auto env = GetJNIEnv();

  auto imageDecoderClass = FindClass(
      env,
      "app/rive/runtime/kotlin/core/ImageDecoder");

  auto decodeToRGBA = env->GetStaticMethodID(
      imageDecoderClass.get(),
      "decodeToRGBA",
      "([B)Landroid/graphics/Bitmap;");

  auto encoded = env->NewByteArray(SizeTToInt(encodedBytes.size()));
  if (!encoded)
  {
    RiveLogE(TAG, "Failed to allocate NewByteArray");
    return nullptr;
  }

  env->SetByteArrayRegion(
      encoded,
      0,
      SizeTToInt(encodedBytes.size()),
      (jbyte*)encodedBytes.data());
  auto jBitmap = JNIExceptionHandler::CallStaticObjectMethod(
      env,
      imageDecoderClass.get(),
      decodeToRGBA,
      encoded);
  env->DeleteLocalRef(encoded); // No longer need encoded

  if (jBitmap == nullptr)
  {
    RiveLogE("RiveN/ImageDecode", "ImageDecoder.decodeToRGBA returned null");
    return nullptr;
  }

  AndroidBitmapInfo info;
  const uint32_t* pixels = nullptr;
  std::unique_ptr<uint8_t[]> out;
  if (lockBitmapRGBA8888(env, jBitmap, &info, &pixels))
  {
    out = copyRGBA8888(info, pixels);
    AndroidBitmap_unlockPixels(env, jBitmap);
    *width = info.width;
    *height = info.height;
  }
  // Frees the pixels now rather than at the next GC.
  env->CallVoidMethod(jBitmap, GetBitmapRecycleMethodId());
  env->DeleteLocalRef(jBitmap);
  return out;
}

rive::rcp<rive::RenderImage> renderImageFromAndroidDecode(
    Span<const uint8_t> encodedBytes,
    RenderContext* renderContext)
{
  uint32_t width;
  uint32_t height;
  auto out = decodeToPremultipliedRGBA(encodedBytes, &width, &height);
  if (out == nullptr)
  {
    return nullptr;
  }

  // New runtime: create backend-specific render images through the active
  // render context.
  // Legacy falls through to AndroidImage below.
  if (renderContext != nullptr)
  {
    return renderContext->createRenderImage(width, height, std::move(out));
  }

  return make_rcp<AndroidImage>(
      static_cast<int>(width),
      static_cast<int>(height),
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
    RiveLogE(TAG, "renderImageFromRGBABytesRive() - Invalid args.");
    return nullptr;
  }
  // Devices without Rive renderer support draw with Canvas. Like the Rive
  // image, the Canvas image copies the pixels.
  if (ResolveRendererType(RendererType::Rive) == RendererType::Canvas)
  {
    return renderImageFromRGBABytesCanvas(
        width,
        height,
        pixelBytes,
        isPremultiplied);
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
  return make_rcp<AndroidImage>(
      static_cast<int>(width),
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
    RiveLogE(TAG, "renderImageFromRGBABytesCanvas() - Invalid args.");
    return nullptr;
  }
  const auto pixelCount = static_cast<size_t>(width) * height;
  auto* env = GetJNIEnv();
  auto jConfig = env->GetStaticObjectField(
      GetAndroidBitmapConfigClass(),
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
    RiveLogE(
        TAG,
        "renderImageFromRGBABytesCanvas() - Bitmap.createBitmap(...) failed.");
    return nullptr;
  }
  auto jCount = static_cast<jsize>(pixelCount);
  auto pixels = env->NewIntArray(jCount);
  if (pixels == nullptr)
  {
    env->DeleteLocalRef(jBitmap);
    RiveLogE(TAG, "renderImageFromRGBABytesCanvas() - NewIntArray failed.");
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
  JNIExceptionHandler::CallVoidMethod(
      env,
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

rive::rcp<rive::RenderImage> renderImageFromARGBIntsRive(
    uint32_t width,
    uint32_t height,
    const uint32_t* pixels,
    bool isPremultiplied)
{
  if (width == 0 || height == 0 || pixels == nullptr)
  {
    RiveLogE(TAG, "renderImageFromARGBIntsRive() - Invalid args.");
    return nullptr;
  }
  // Devices without Rive renderer support draw with Canvas.
  if (ResolveRendererType(RendererType::Rive) == RendererType::Canvas)
  {
    return renderImageFromARGBIntsCanvas(
        width,
        height,
        pixels,
        isPremultiplied);
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
  return make_rcp<AndroidImage>(
      static_cast<int>(width),
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
    RiveLogE(TAG, "renderImageFromARGBIntsCanvas() - Invalid args.");
    return nullptr;
  }
  const auto pixelCount = static_cast<size_t>(width) * height;
  auto* env = GetJNIEnv();
  auto jConfig = env->GetStaticObjectField(
      GetAndroidBitmapConfigClass(),
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
    RiveLogE(
        TAG,
        "renderImageFromARGBIntsCanvas() - Bitmap.createBitmap(...) failed.");
    return nullptr;
  }
  auto count = static_cast<jsize>(pixelCount);
  auto dstPixels = env->NewIntArray(count);
  if (dstPixels == nullptr)
  {
    env->DeleteLocalRef(jBitmap);
    RiveLogE(TAG, "renderImageFromARGBIntsCanvas() - NewIntArray failed.");
    return nullptr;
  }
  if (!isPremultiplied)
  {
    env->SetIntArrayRegion(
        dstPixels,
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
  JNIExceptionHandler::CallVoidMethod(
      env,
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

rive::rcp<rive::RenderImage> renderImageFromBitmapRive(
    jobject jBitmap,
    bool isPremultiplied)
{
  if (jBitmap == nullptr)
  {
    RiveLogE(TAG, "renderImageFromBitmapRive() - Bitmap was null.");
    return nullptr;
  }
  auto* env = GetJNIEnv();
  AndroidBitmapInfo info;
  const uint32_t* srcPixels = nullptr;
  if (!lockBitmapRGBA8888(env, jBitmap, &info, &srcPixels))
  {
    RiveLogE(TAG, "renderImageFromBitmapRive() - Failed to lock srcPixels.");
    return nullptr;
  }

  auto dstBytes = copyRGBA8888(info, srcPixels);

  // Always unlock srcPixels
  AndroidBitmap_unlockPixels(env, jBitmap);

  // The packed buffer is RGBA. Route through the RGBA path that handles
  // isPremultiplied/straight alpha
  return renderImageFromRGBABytesRive(
      info.width,
      info.height,
      dstBytes.get(),
      isPremultiplied);
}

rive::rcp<rive::RenderImage> renderImageFromBitmapCanvas(jobject jBitmap)
{
  if (jBitmap == nullptr)
  {
    RiveLogE(TAG, "renderImageFromBitmapCanvas() - Bitmap was null.");
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

std::unique_ptr<uint8_t[]> copyRGBA8888(
    const AndroidBitmapInfo& info,
    const uint32_t* pixels)
{
  const auto rowBytes = static_cast<size_t>(info.width) * 4;
  std::unique_ptr<uint8_t[]> dst(new uint8_t[rowBytes * info.height]);
  const auto* src = reinterpret_cast<const uint8_t*>(pixels);
  if (info.stride == rowBytes)
  {
    memcpy(dst.get(), src, rowBytes * info.height);
  }
  else
  {
    for (size_t y = 0; y < info.height; ++y)
    {
      memcpy(dst.get() + y * rowBytes, src + y * info.stride, rowBytes);
    }
  }
  return dst;
}

bool lockBitmapRGBA8888(
    JNIEnv* env,
    jobject jBitmap,
    AndroidBitmapInfo* info,
    const uint32_t** pixels)
{
  if (AndroidBitmap_getInfo(env, jBitmap, info) !=
      ANDROID_BITMAP_RESULT_SUCCESS)
  {
    RiveLogE(TAG, "lockBitmapRGBA8888() - AndroidBitmap_getInfo failed.");
    return false;
  }
  if (info->format != ANDROID_BITMAP_FORMAT_RGBA_8888)
  {
    RiveLogE(TAG, "lockBitmapRGBA8888() - Unexpected bitmap format.");
    return false;
  }
  if (info->width == 0 || info->height == 0)
  {
    RiveLogE(TAG, "lockBitmapRGBA8888() - Invalid dimensions.");
    return false;
  }
  void* lockedPixelsPtr = nullptr;
  auto lockResult = AndroidBitmap_lockPixels(env, jBitmap, &lockedPixelsPtr);
  if (lockResult != ANDROID_BITMAP_RESULT_SUCCESS || lockedPixelsPtr == nullptr)
  {
    if (lockResult == ANDROID_BITMAP_RESULT_BAD_PARAMETER)
    {
      RiveLogE(
          TAG,
          "lockBitmapRGBA8888() - Failed to lock pixes: bad parameter.");
    }
    else if (lockResult == ANDROID_BITMAP_RESULT_JNI_EXCEPTION)
    {
      RiveLogE(
          TAG,
          "lockBitmapRGBA8888() - Failed to lock pixes: JNI exception occurred.");
    }
    else if (lockResult == ANDROID_BITMAP_RESULT_ALLOCATION_FAILED)
    {
      RiveLogE(
          TAG,
          "lockBitmapRGBA8888() - Failed to lock pixes: allocation failed.");
    }
    else
    {
      RiveLogE(
          TAG,
          "lockBitmapRGBA8888() - Failed to lock pixes: unknown error: %d",
          lockResult);
    }

    return false;
  }
  *pixels = reinterpret_cast<uint32_t*>(lockedPixelsPtr);
  return true;
}

} // namespace rive_android
