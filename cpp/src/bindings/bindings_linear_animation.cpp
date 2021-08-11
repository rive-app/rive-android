#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/animation/linear_animation.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

    // ANIMATION
    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_Animation_cppName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        auto *animation = (const rive::LinearAnimation *)ref;
        return env->NewStringUTF(animation->name().c_str());
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_Animation_cppDuration(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        auto *animation = (const rive::LinearAnimation *)ref;
        return (jint)animation->duration();
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_Animation_cppFps(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        auto *animation = (const rive::LinearAnimation *)ref;
        return (jint)animation->fps();
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_Animation_cppWorkStart(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        auto *animation = (const rive::LinearAnimation *)ref;
        return (jint)animation->workStart();
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_Animation_cppWorkEnd(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        auto *animation = (const rive::LinearAnimation *)ref;
        return (jint)animation->workEnd();
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_Animation_cppGetLoop(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        auto *animation = (const rive::LinearAnimation *)ref;
        return (jint)animation->loop();
    }

#ifdef __cplusplus
}
#endif
