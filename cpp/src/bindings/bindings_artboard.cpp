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

    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_Artboard_nativeName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return env->NewStringUTF(artboard->name().c_str());
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_nativeFirstAnimation(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return (jlong)artboard->firstAnimation();
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_nativeAnimationByIndex(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jint index)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;
        return (jlong)artboard->animation(index);
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_nativeAnimationByName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jstring name)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return (jlong)artboard->animation(
            jstring2string(env, name));
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_nativeAnimationCount(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;

        return (jint)artboard->animationCount();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_nativeAdvance(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jfloat elapsedTime)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;
        artboard->advance(elapsedTime);
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_nativeBounds(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::Artboard *artboard = (rive::Artboard *)ref;
        // TODO: garbage collection?
        auto bounds = new rive::AABB(artboard->bounds());
        return (jlong)bounds;
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_nativeDraw(
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

#ifdef __cplusplus
}
#endif
