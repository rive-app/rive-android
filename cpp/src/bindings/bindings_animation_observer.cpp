#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/animation_observer.hpp"

// From rive-cpp
#include "animation/linear_animation_instance.hpp"
//

#include <jni.h>

#define FUNCTION(name) Java_app_rive_runtime_kotlin_AnimationObserver_##name

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    JNIEXPORT jlong JNICALL FUNCTION(constructor)(
    // JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_AnimationObserver_constructor(
        JNIEnv *env,
        jobject thisObj,
        jlong animationRef)
    {
        ::globalJNIEnv = env;

        auto observerInstance = new AnimationObserver(env, thisObj);

        return (jlong)observerInstance;
    }
#ifdef __cplusplus
}
#endif
