#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/animation_observer.hpp"

// From rive-cpp
#include "animation/linear_animation_instance.hpp"
//

#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    // ANIMATION INSTANCE
    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_LinearAnimationInstance_constructor(
        JNIEnv *env,
        jobject thisObj,
        jlong animationRef)
    {
        ::globalJNIEnv = env;

        rive::LinearAnimation *animation = (rive::LinearAnimation *)animationRef;
        auto animationInstance = new rive::LinearAnimationInstance(animation);

        return (jlong)animationInstance;
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_LinearAnimationInstance_nativeAdvance(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jfloat elapsedTime)
    {
        ::globalJNIEnv = env;

        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        animationInstance->advance(elapsedTime);
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_LinearAnimationInstance_nativeApply(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jlong artbaordRef,
        jfloat mix)
    {
        ::globalJNIEnv = env;

        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        rive::Artboard *artboard = (rive::Artboard *)artbaordRef;
        animationInstance->apply(artboard, mix);
    }

    JNIEXPORT jfloat JNICALL Java_app_rive_runtime_kotlin_LinearAnimationInstance_nativeGetTime(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        ::globalJNIEnv = env;

        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        return animationInstance->time();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_LinearAnimationInstance_nativeSetTime(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jfloat time)
    {
        ::globalJNIEnv = env;

        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        animationInstance->time(time);
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_LinearAnimationInstance_nativeAddObserver(JNIEnv *env, jobject thisObj, jlong ref, jlong observerRef)
    {
        ::globalJNIEnv = env;
        auto animationInstance = (rive::LinearAnimationInstance *)ref;
        auto observer = (AnimationObserver *)observerRef;
        animationInstance->attachObserver(observer);
    }

#ifdef __cplusplus
}
#endif
