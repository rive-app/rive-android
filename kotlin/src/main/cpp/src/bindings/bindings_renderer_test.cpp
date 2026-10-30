/**
 * Testing functions for renderer selection.
 */
#ifdef DEBUG

#include <jni.h>
#include <vector>

#include "helpers/canvas_render_objects.hpp"
#include "helpers/image_decode.hpp"
#include "helpers/worker_ref.hpp"
#include "rive/renderer/rive_render_image.hpp"
#include "utils/lite_rtti.hpp"

#ifdef __cplusplus
extern "C"
{
#endif
  using namespace rive_android;

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_kotlin_core_NativeRendererTestHelper_cppSimulateUnsupportedRiveRenderer(
      JNIEnv*,
      jobject,
      jboolean simulate)
  {
    RefWorker::SimulateUnsupportedRiveRenderer(simulate == JNI_TRUE);
  }

  JNIEXPORT jboolean JNICALL
  Java_app_rive_runtime_kotlin_core_NativeRendererTestHelper_cppIsRiveRendererSupported(
      JNIEnv*,
      jobject)
  {
    return RefWorker::RiveWorker() != nullptr;
  }

  JNIEXPORT jboolean JNICALL
  Java_app_rive_runtime_kotlin_core_NativeRendererTestHelper_cppIsRiveRenderImage(
      JNIEnv*,
      jobject,
      jlong address)
  {
    auto* image = reinterpret_cast<rive::RenderImage*>(address);
    return rive::lite_rtti_cast<rive::RiveRenderImage*>(image) != nullptr;
  }

  JNIEXPORT jboolean JNICALL
  Java_app_rive_runtime_kotlin_core_NativeRendererTestHelper_cppCanvasImageWrapsBitmap(
      JNIEnv* env,
      jobject,
      jlong address,
      jobject jBitmap)
  {
    auto* image = reinterpret_cast<rive::RenderImage*>(address);
    if (image == nullptr ||
        rive::lite_rtti_cast<rive::RiveRenderImage*>(image) != nullptr)
    {
      return JNI_FALSE;
    }
    auto* canvasImage = static_cast<CanvasRenderImage*>(image);
    return env->IsSameObject(canvasImage->ktBitmap(), jBitmap);
  }

  JNIEXPORT jbyteArray JNICALL
  Java_app_rive_runtime_kotlin_core_NativeRendererTestHelper_cppDecodeImage(
      JNIEnv* env,
      jobject,
      jbyteArray jEncoded,
      jintArray jSize)
  {
    auto count = env->GetArrayLength(jEncoded);
    std::vector<uint8_t> encoded(count);
    env->GetByteArrayRegion(
        jEncoded,
        0,
        count,
        reinterpret_cast<jbyte*>(encoded.data()));
    uint32_t width;
    uint32_t height;
    auto pixels = decodeToPremultipliedRGBA(
        rive::make_span(encoded.data(), encoded.size()),
        &width,
        &height);
    if (pixels == nullptr)
    {
      return nullptr;
    }
    jint size[] = {static_cast<jint>(width), static_cast<jint>(height)};
    env->SetIntArrayRegion(jSize, 0, 2, size);
    auto byteCount = static_cast<jsize>(width * height * 4);
    auto out = env->NewByteArray(byteCount);
    env->SetByteArrayRegion(
        out,
        0,
        byteCount,
        reinterpret_cast<const jbyte*>(pixels.get()));
    return out;
  }

#ifdef __cplusplus
}
#endif

#endif // DEBUG
