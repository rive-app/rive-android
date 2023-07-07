#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/animation/loop.hpp"
#include "rive/animation/linear_animation_instance.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    JNIEXPORT jobject JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppAdvance(JNIEnv* env,
                                                                         jobject thisObj,
                                                                         jlong ref,
                                                                         jfloat elapsedTime)
    {
        auto animationInstance = (rive::LinearAnimationInstance*)ref;
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

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppApply(JNIEnv* env,
                                                                       jobject thisObj,
                                                                       jlong ref,
                                                                       jfloat mix)
    {
        auto animationInstance = (rive::LinearAnimationInstance*)ref;
        animationInstance->apply(mix);
    }

    JNIEXPORT jfloat JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppGetTime(JNIEnv* env,
                                                                         jobject thisObj,
                                                                         jlong ref)
    {

        auto animationInstance = (rive::LinearAnimationInstance*)ref;
        return animationInstance->time();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppSetTime(JNIEnv* env,
                                                                         jobject thisObj,
                                                                         jlong ref,
                                                                         jfloat time)
    {
        auto animationInstance = (rive::LinearAnimationInstance*)ref;
        animationInstance->time(time);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppSetDirection(JNIEnv* env,
                                                                              jobject thisObj,
                                                                              jlong ref,
                                                                              jint direction)
    {
        auto animationInstance = (rive::LinearAnimationInstance*)ref;
        animationInstance->direction(direction);
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppGetDirection(JNIEnv* env,
                                                                              jobject thisObj,
                                                                              jlong ref)
    {
        auto animationInstance = (rive::LinearAnimationInstance*)ref;
        return animationInstance->direction();
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppGetLoop(JNIEnv* env,
                                                                         jobject thisObj,
                                                                         jlong ref)
    {
        auto* animationInstance = (rive::LinearAnimationInstance*)ref;
        return (jint)animationInstance->loop();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppSetLoop(JNIEnv* env,
                                                                         jobject thisObj,
                                                                         jlong ref,
                                                                         jint loopType)
    {
        auto* animationInstance = (rive::LinearAnimationInstance*)ref;

        animationInstance->loopValue(loopType);
    }

    // ANIMATION
    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppName(JNIEnv* env,
                                                                      jobject thisObj,
                                                                      jlong ref)
    {

        auto* animationInstance = (const rive::LinearAnimationInstance*)ref;
        return env->NewStringUTF(animationInstance->animation()->name().c_str());
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppDuration(JNIEnv* env,
                                                                          jobject thisObj,
                                                                          jlong ref)
    {
        auto* animationInstance = (const rive::LinearAnimationInstance*)ref;
        return (jint)animationInstance->animation()->duration();
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppFps(JNIEnv* env,
                                                                     jobject thisObj,
                                                                     jlong ref)
    {
        auto* animationInstance = (const rive::LinearAnimationInstance*)ref;
        return (jint)animationInstance->animation()->fps();
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppWorkStart(JNIEnv* env,
                                                                           jobject thisObj,
                                                                           jlong ref)
    {
        auto* animationInstance = (const rive::LinearAnimationInstance*)ref;
        return (jint)animationInstance->animation()->workStart();
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppWorkEnd(JNIEnv* env,
                                                                         jobject thisObj,
                                                                         jlong ref)
    {
        auto* animationInstance = (const rive::LinearAnimationInstance*)ref;
        return (jint)animationInstance->animation()->workEnd();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_LinearAnimationInstance_cppDelete(JNIEnv*, jobject, jlong ref)
    {
        auto animationInstance = (rive::LinearAnimationInstance*)ref;
        delete animationInstance;
    }

#ifdef __cplusplus
}
#endif
