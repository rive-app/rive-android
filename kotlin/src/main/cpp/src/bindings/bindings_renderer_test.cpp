/**
 * Testing functions for renderer selection.
 */
#ifdef DEBUG

#include <jni.h>

#include "helpers/canvas_render_objects.hpp"
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

#ifdef __cplusplus
}
#endif

#endif // DEBUG
