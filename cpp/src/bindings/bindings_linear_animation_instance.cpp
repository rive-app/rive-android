#include "jni_refs.hpp"
#include "helpers/general.hpp"

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

        // TODO: delete this object?
        auto animationInstance = new rive::LinearAnimationInstance(animation);

        return (jlong)animationInstance;
    }

    JNIEXPORT jobject JNICALL Java_app_rive_runtime_kotlin_LinearAnimationInstance_nativeAdvance(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jfloat elapsedTime)
    {
        ::globalJNIEnv = env;

        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        animationInstance->advance(elapsedTime);
        bool didLoop = animationInstance->didLoop();

        jfieldID enumField;
        jobject loopValue = nullptr;

        if (didLoop)
        {
            auto loopType = animationInstance->animation()->loop();
            switch (loopType)
            {
            case rive::Loop::oneShot:
                enumField = ::oneShotLoopField;
                break;
            case rive::Loop::loop:
                enumField = ::loopLoopField;
                break;
            case rive::Loop::pingPong:
                enumField = ::pingPongLoopField;
                break;
            default:
                enumField = ::noneLoopField;
                break;
            }
            
            loopValue = env->GetStaticObjectField(::loopClass, enumField);
        }

        return loopValue;
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
