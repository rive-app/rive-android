#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/animation/layer_state.hpp"
#include "rive/animation/exit_state.hpp"
#include "rive/animation/entry_state.hpp"
#include "rive/animation/blend_state.hpp"
#include "rive/animation/blend_state_direct.hpp"
#include "rive/animation/blend_state_1d.hpp"
#include "rive/animation/any_state.hpp"
#include "rive/animation/animation_state.hpp"

#include "rive/animation/linear_animation_instance.hpp"

#ifdef __cplusplus
extern "C" {
#endif

// ANIMATION
JNIEXPORT jboolean JNICALL
Java_app_rive_runtime_kotlin_core_LayerState_cppIsExitState(JNIEnv* env,
                                                            jobject thisObj,
                                                            jlong ref) {

    rive::LayerState* layerState = (rive::LayerState*)ref;
    return layerState->is<rive::ExitState>();
}

JNIEXPORT jboolean JNICALL
Java_app_rive_runtime_kotlin_core_LayerState_cppIsAnyState(JNIEnv* env,
                                                           jobject thisObj,
                                                           jlong ref) {

    rive::LayerState* layerState = (rive::LayerState*)ref;
    return layerState->is<rive::AnyState>();
}

JNIEXPORT jboolean JNICALL
Java_app_rive_runtime_kotlin_core_LayerState_cppIsEntryState(JNIEnv* env,
                                                             jobject thisObj,
                                                             jlong ref) {

    rive::LayerState* layerState = (rive::LayerState*)ref;
    return layerState->is<rive::EntryState>();
}

JNIEXPORT jboolean JNICALL
Java_app_rive_runtime_kotlin_core_LayerState_cppIsAnimationState(JNIEnv* env,
                                                                 jobject thisObj,
                                                                 jlong ref) {

    rive::LayerState* layerState = (rive::LayerState*)ref;
    return layerState->is<rive::AnimationState>();
}

JNIEXPORT jboolean JNICALL
Java_app_rive_runtime_kotlin_core_LayerState_cppIsBlendState(JNIEnv* env,
                                                             jobject thisObj,
                                                             jlong ref) {

    rive::LayerState* layerState = (rive::LayerState*)ref;
    return layerState->is<rive::BlendState>();
}

JNIEXPORT jboolean JNICALL
Java_app_rive_runtime_kotlin_core_LayerState_cppIsBlendState1D(JNIEnv* env,
                                                               jobject thisObj,
                                                               jlong ref) {

    rive::LayerState* layerState = (rive::LayerState*)ref;
    return layerState->is<rive::BlendState1D>();
}

JNIEXPORT jboolean JNICALL
Java_app_rive_runtime_kotlin_core_LayerState_cppIsBlendStateDirect(JNIEnv* env,
                                                                   jobject thisObj,
                                                                   jlong ref) {

    rive::LayerState* layerState = (rive::LayerState*)ref;
    return layerState->is<rive::BlendStateDirect>();
}

JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_AnimationState_cppName(JNIEnv* env,
                                                                                   jobject thisObj,
                                                                                   jlong ref) {

    auto animationState = (rive::AnimationState*)ref;
    // urgh this animation state is using forward declarations...
    return env->NewStringUTF(animationState->animation()->name().c_str());
}

#ifdef __cplusplus
}
#endif
