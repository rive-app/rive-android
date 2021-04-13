#include "helpers/general.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Rive_nativeInitialize(
        JNIEnv *env,
        jobject thisObj)
    {
        // pretty much considered the entrypoint.
        env->GetJavaVM(&::globalJavaVM);
    }

#ifdef __cplusplus
}
#endif
