/**
 * Testing functions for the Rive worker thread.
 */
#ifdef DEBUG

#include <jni.h>

#include "helpers/general.hpp"
#include "helpers/worker_ref.hpp"

#ifdef __cplusplus
extern "C"
{
#endif
  using namespace rive_android;

  JNIEXPORT jboolean JNICALL
  Java_app_rive_runtime_kotlin_core_NativeWorkerTestHelper_cppRunOnRiveWorker(
      JNIEnv* env,
      jobject,
      jobject runnable)
  {
    rive::rcp<RefWorker> worker = RefWorker::RiveWorker();
    if (worker == nullptr)
    {
      // The Rive renderer isn't supported on this device.
      return JNI_FALSE;
    }
    jobject globalRunnable = env->NewGlobalRef(runnable);
    worker->run([globalRunnable](DrawableThreadState*) {
      JNIEnv* workerEnv = GetJNIEnv();
      jclass runnableClass = workerEnv->GetObjectClass(globalRunnable);
      jmethodID run = workerEnv->GetMethodID(runnableClass, "run", "()V");
      workerEnv->CallVoidMethod(globalRunnable, run);
      if (workerEnv->ExceptionCheck())
      {
        // Don't leave it pending for the worker's next JNI call.
        workerEnv->ExceptionDescribe();
        workerEnv->ExceptionClear();
      }
      workerEnv->DeleteLocalRef(runnableClass);
      workerEnv->DeleteGlobalRef(globalRunnable);
    });
    return JNI_TRUE;
  }

#ifdef __cplusplus
}
#endif

#endif // DEBUG
