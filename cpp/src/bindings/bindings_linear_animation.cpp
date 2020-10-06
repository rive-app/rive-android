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

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_Animation_nativeDuration(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive_android::globalJNIEnv = env;
        auto *animation = (rive::LinearAnimation *)ref;
        return (jint)animation->duration();
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_Animation_nativeFps(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive_android::globalJNIEnv = env;
        auto *animation = (rive::LinearAnimation *)ref;
        return (jint)animation->fps();
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_Animation_nativeWorkStart(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive_android::globalJNIEnv = env;
        auto *animation = (rive::LinearAnimation *)ref;
        return (jint)animation->workStart();
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_Animation_nativeWorkEnd(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive_android::globalJNIEnv = env;
        auto *animation = (rive::LinearAnimation *)ref;
        return (jint)animation->workEnd();
    }
    
    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_Animation_nativeGetLoop(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive_android::globalJNIEnv = env;
        auto * animation = (rive::LinearAnimation*) ref;
        return (jint)animation->loop();
    }
    
    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_Animation_nativeSetLoop(
        JNIEnv *env,
        jobject thisObj,
        jlong ref, 
        jint loopType)
    {
        rive_android::globalJNIEnv = env;
        auto *animation = (rive::LinearAnimation *)ref;
        
        animation->loopValue(loopType);
    }    

#ifdef __cplusplus
}
#endif
