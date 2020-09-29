#include "jni_refs.hpp"
#include "helpers/general.hpp"

// From rive-cpp
#include "animation/linear_animation_instance.hpp"
//

#ifdef __cplusplus
extern "C"
{
#endif

    // ANIMATION
    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_Animation_nativeName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive_android::globalJNIEnv = env;

        rive::LinearAnimation *animation = (rive::LinearAnimation *)ref;
        return env->NewStringUTF(animation->name().c_str());
    }

#ifdef __cplusplus
}
#endif
