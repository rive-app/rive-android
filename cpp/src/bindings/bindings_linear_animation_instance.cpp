#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/animation_observer.hpp"

// From rive-cpp
#include "animation/linear_animation_instance.hpp"
#include "animation/loop.hpp"
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

    jfieldID getJavaLoop(JNIEnv *env, const char *name)
    {
        return env->GetStaticFieldID(loopClass, name, "Lapp/rive/runtime/kotlin/Loop;");
    }

    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_LinearAnimationInstance_nativeAdvance(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jfloat elapsedTime)
    {
        ::globalJNIEnv = env;

        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        rive::Loop loop;
        animationInstance->advance(elapsedTime, loop);

        switch (loop)
        {
        case rive::Loop::oneShot:
            return env->NewStringUTF("ONESHOT");
        case rive::Loop::loop:
            return env->NewStringUTF("LOOP");
        case rive::Loop::pingPong:
            return env->NewStringUTF("PINGPONG");
        default:
            return nullptr;
        }
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_LinearAnimationInstance_nativeApply(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jlong artboardRef,
        jfloat mix)
    {
        ::globalJNIEnv = env;

        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        rive::Artboard *artboard = (rive::Artboard *)artboardRef;
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

#ifdef __cplusplus
}
#endif
