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
    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_constructor(
        JNIEnv *env,
        jobject thisObj,
        jlong animationRef)
    {

        rive::LinearAnimation *animation = (rive::LinearAnimation *)animationRef;

        // TODO: delete this object?
        rive::LinearAnimationInstance *animationInstance = new rive::LinearAnimationInstance(animation);

        return (jlong)animationInstance;
    }

    JNIEXPORT jobject JNICALL Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppAdvance(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jfloat elapsedTime)
    {
        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        animationInstance->advance(elapsedTime);
        bool didLoop = animationInstance->didLoop();

        jfieldID enumField;
        jobject loopValue = nullptr;

        if (didLoop)
        {
            rive::Loop loopType = animationInstance->loop();
            switch (loopType)
            {
            case rive::Loop::oneShot:
                enumField = ::getOneShotLoopField();
                break;
            case rive::Loop::loop:
                enumField = ::getLoopLoopField();
                break;
            case rive::Loop::pingPong:
                enumField = ::getPingPongLoopField();
                break;
            default:
                enumField = ::getNoneLoopField();
                break;
            }
            jclass jClass = ::getLoopClass();
            loopValue = env->GetStaticObjectField(jClass, enumField);
            env->DeleteLocalRef(jClass);
        }

        return loopValue;
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppApply(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jlong artboardRef,
        jfloat mix)
    {
        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        rive::Artboard *artboard = (rive::Artboard *)artboardRef;
        animationInstance->apply(artboard, mix);
    }

    JNIEXPORT jfloat JNICALL Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppGetTime(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        return animationInstance->time();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppSetTime(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jfloat time)
    {
        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        animationInstance->time(time);
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppSetDirection(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jint direction)
    {
        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        animationInstance->direction(direction);
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppGetDirection(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::LinearAnimationInstance *animationInstance = (rive::LinearAnimationInstance *)ref;
        return animationInstance->direction();
    }


    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppGetLoop(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        auto *animationInstance = (rive::LinearAnimationInstance *)ref;
        return (jint)animationInstance->loop();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppSetLoop(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jint loopType)
    {
        auto *animationInstance = (rive::LinearAnimationInstance *)ref;

        animationInstance->loopValue(loopType);
    }

#ifdef __cplusplus
}
#endif
