#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/jni_renderer.hpp"

// From rive-cpp
#include "artboard.hpp"
#include "animation/linear_animation_instance.hpp"
//

#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif

    using namespace rive_android;

    // ARTBOARD

    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return env->NewStringUTF(artboard->name().c_str());
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppFirstAnimation(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return (jlong)artboard->firstAnimation();
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppFirstStateMachine(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return (jlong)artboard->firstStateMachine();
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationByIndex(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jint index)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;
        return (jlong)artboard->animation(index);
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationByName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jstring name)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return (jlong)artboard->animation(
            jstring2string(env, name));
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationCount(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return (jint)artboard->animationCount();
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineByIndex(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jint index)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;
        return (jlong)artboard->stateMachine(index);
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineByName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jstring name)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return (jlong)artboard->stateMachine(
            jstring2string(env, name));
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineCount(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return (jint)artboard->stateMachineCount();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppAdvance(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jfloat elapsedTime)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;
        artboard->advance(elapsedTime);
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppBounds(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;
        // TODO: garbage collection?
        auto bounds = new rive::AABB(artboard->bounds());
        return (jlong)bounds;
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppDraw(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jlong rendererRef,
        jobject rendererObj)
    {

        rive::Artboard *artboard = (rive::Artboard *)ref;
        ::JNIRenderer *renderer = (::JNIRenderer *)rendererRef;
        artboard->draw(renderer);
    }

    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppIsInstance(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return artboard->isInstance();
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppInstance(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return (jlong)artboard->instance();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppDelete(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;
        delete artboard;
    }

#ifdef __cplusplus
}
#endif
