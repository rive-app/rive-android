/**
 * Testing functions
 */
#ifdef DEBUG

#include <jni.h>

#include "rive/viewmodel/runtime/viewmodel_instance_runtime.hpp"

extern "C"
{
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
