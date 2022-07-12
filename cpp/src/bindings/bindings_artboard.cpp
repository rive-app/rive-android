#include "jni_refs.hpp"
#include "skia_renderer.hpp"
#include "helpers/general.hpp"
#include "models/jni_renderer_skia.hpp"
#include "rive/artboard.hpp"
#include "rive/animation/linear_animation_instance.hpp"
#include "rive/animation/state_machine_instance.hpp"
#include <jni.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

using namespace rive_android;

// ARTBOARD

JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppName(JNIEnv* env,
                                                                             jobject thisObj,
                                                                             jlong ref) {
    auto artboard = (rive::ArtboardInstance*)ref;
    return env->NewStringUTF(artboard->name().c_str());
}

JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppFirstAnimation(
    JNIEnv* env, jobject thisObj, jlong ref) {
    auto artboard = (rive::ArtboardInstance*)ref;
    // Creates a new instance.
    return (jlong)artboard->animationAt(0).release();
}

JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppFirstStateMachine(
    JNIEnv* env, jobject thisObj, jlong ref) {
    auto artboard = (rive::ArtboardInstance*)ref;
    // Creates a new instance.
    return (jlong)artboard->stateMachineAt(0).release();
}

JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationNameByIndex(
    JNIEnv* env, jobject, jlong ref, jint index) {
    auto artboard = (rive::ArtboardInstance*)ref;

    rive::LinearAnimation* animation = artboard->animation(index);
    auto name = animation->name();

    return env->NewStringUTF(name.c_str());
}

JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineNameByIndex(
    JNIEnv* env, jobject, jlong ref, jint index) {
    auto artboard = (rive::ArtboardInstance*)ref;

    rive::StateMachine* stateMachine = artboard->stateMachine(index);
    auto name = stateMachine->name();

    return env->NewStringUTF(name.c_str());
}

JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationByIndex(
    JNIEnv* env, jobject thisObj, jlong ref, jint index) {
    auto artboard = (rive::ArtboardInstance*)ref;
    // Creates a new instance.
    return (jlong)artboard->animationAt(index).release();
}

JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationByName(
    JNIEnv* env, jobject thisObj, jlong ref, jstring name) {
    auto artboard = (rive::ArtboardInstance*)ref;
    // Creates a new instance.
    return (jlong)artboard->animationNamed(jstring2string(env, name)).release();
}

JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationCount(JNIEnv* env,
                                                                                    jobject thisObj,
                                                                                    jlong ref) {
    auto artboard = (rive::ArtboardInstance*)ref;

    return (jint)artboard->animationCount();
}

JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineByIndex(
    JNIEnv* env, jobject thisObj, jlong ref, jint index) {
    auto artboard = (rive::ArtboardInstance*)ref;
    // Creates a new instance.
    return (jlong)artboard->stateMachineAt(index).release();
}

JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineByName(
    JNIEnv* env, jobject thisObj, jlong ref, jstring name) {
    auto artboard = (rive::ArtboardInstance*)ref;
    // Creates a new instance.

    return (jlong)artboard->stateMachineNamed(jstring2string(env, name)).release();
}

JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineCount(
    JNIEnv* env, jobject thisObj, jlong ref) {
    auto artboard = (rive::ArtboardInstance*)ref;

    return (jint)artboard->stateMachineCount();
}

JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppAdvance(
    JNIEnv* env, jobject thisObj, jlong ref, jfloat elapsedTime) {
    auto artboard = (rive::ArtboardInstance*)ref;
    return artboard->advance(elapsedTime);
}

JNIEXPORT jobject JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppBounds(JNIEnv* env,
                                                                               jobject thisObj,
                                                                               jlong ref) {
    auto cls = env->FindClass("android/graphics/RectF");
    auto constructor = env->GetMethodID(cls, "<init>", "(FFFF)V");
    const auto bounds = ((rive::ArtboardInstance*)ref)->bounds();
    return env->NewObject(
        cls, constructor, bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
}

JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppDrawSkia(JNIEnv* env,
                                                                              jobject,
                                                                              jlong artboardRef,
                                                                              jlong rendererRef) {
    // TODO: consolidate this to work with an abstracted JNI Renderer.
    auto artboard = (rive::ArtboardInstance*)artboardRef;
    auto jniWrapper = (JNIRendererSkia*)rendererRef;
    rive::SkiaRenderer* renderer = jniWrapper->skRenderer();
    artboard->draw(renderer);
}

JNIEXPORT void JNICALL
Java_app_rive_runtime_kotlin_core_Artboard_cppDrawSkiaAligned(JNIEnv* env,
                                                              jobject,
                                                              jlong artboardRef,
                                                              jlong rendererRef,
                                                              jobject ktFit,
                                                              jobject ktAlignment) {
    // TODO: consolidate this to work with an abstracted JNI Renderer.
    auto artboard = (rive::ArtboardInstance*)artboardRef;
    auto jniWrapper = (JNIRendererSkia*)rendererRef;
    rive::SkiaRenderer* renderer = jniWrapper->skRenderer();

    rive::Fit fit = getFit(env, ktFit);
    rive::Alignment alignment = getAlignment(env, ktAlignment);

    renderer->save();
    renderer->align(fit,
                    alignment,
                    rive::AABB(0, 0, jniWrapper->width(), jniWrapper->height()),
                    artboard->bounds());
    artboard->draw(renderer);
    renderer->restore();
}

JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppDelete(JNIEnv* env,
                                                                            jobject thisObj,
                                                                            jlong ref) {
    auto artboard = (rive::ArtboardInstance*)ref;
    delete artboard;
}

#ifdef __cplusplus
}
#endif
