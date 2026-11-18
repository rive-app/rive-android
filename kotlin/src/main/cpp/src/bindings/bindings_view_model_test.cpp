/**
 * Testing functions
 */
#ifdef DEBUG

#include <algorithm>
#include <chrono>
#include <future>
#include <jni.h>

#include "helpers/jni_resource.hpp"
#include "rive/command_queue.hpp"
#include "rive/command_server.hpp"
#include "rive/viewmodel/runtime/viewmodel_instance_runtime.hpp"

extern "C"
{
  JNIEXPORT jboolean JNICALL Java_app_rive_ViewModelInstanceTest_cppBlobMatches(
      JNIEnv* env,
      jobject,
      jobject worker,
      jlong instance,
      jbyteArray expected)
  {
    auto workerClass = env->GetObjectClass(worker);
    auto pointerMethod = env->GetMethodID(
        workerClass,
        "requireNativePointer",
        "()J");
    auto pointer = env->CallLongMethod(worker, pointerMethod);
    env->DeleteLocalRef(workerClass);
    if (env->ExceptionCheck())
    {
      return JNI_FALSE;
    }
    auto queue = reinterpret_cast<rive::CommandQueue*>(pointer);
    bool cleared = expected == nullptr;
    auto bytes = cleared ? std::vector<uint8_t>()
                         : rive_android::ByteArrayToUint8Vec(env, expected);
    auto result = std::make_shared<std::promise<bool>>();
    auto future = result->get_future();
    // Capture owned state because a timed-out test must not leave dangling
    // stack references.
    queue->runOnce([instance, cleared, bytes = std::move(bytes), result](
                       rive::CommandServer* server) {
      auto model = server->getViewModelInstance(
          reinterpret_cast<rive::ViewModelInstanceHandle>(
              static_cast<uint64_t>(instance)));
      auto property = model ? model->propertyBlob("xml") : nullptr;
      if (!property)
      {
        result->set_value(false);
        return;
      }
      auto blob = property->viewModelInstanceValue()
                      ->as<rive::ViewModelInstanceAssetBlob>()
                      ->asset();
      result->set_value(
          cleared ? !blob
                  : blob && blob->bytes().size() == bytes.size() &&
                        std::equal(
                            bytes.begin(),
                            bytes.end(),
                            blob->bytes().begin()));
    });
    return future.wait_for(std::chrono::seconds(5)) ==
               std::future_status::ready &&
           future.get();
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_kotlin_core_NativeViewModelTestHelper_cppRefInstance(
      JNIEnv*,
      jobject,
      jlong ref)
  {
    reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref)->ref();
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_kotlin_core_NativeViewModelTestHelper_cppUnrefInstance(
      JNIEnv*,
      jobject,
      jlong ref)
  {
    reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref)->unref();
  }

  JNIEXPORT jint JNICALL
  Java_app_rive_runtime_kotlin_core_NativeViewModelTestHelper_cppInstanceRefCount(
      JNIEnv*,
      jobject,
      jlong ref)
  {
    return reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref)
        ->debugging_refcnt();
  }
}

#endif // DEBUG
