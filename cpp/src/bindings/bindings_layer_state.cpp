#include "jni_refs.hpp"
#include "helpers/general.hpp"

// From rive-cpp
#include "animation/layer_state.hpp"
#include "animation/exit_state.hpp"
#include "animation/entry_state.hpp"
#include "animation/any_state.hpp"
#include "animation/animation_state.hpp"
#include "animation/blend_state.hpp"
#include "animation/blend_state_direct.hpp"
#include "animation/blend_state_1d.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

    // ANIMATION
    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_LayerState_cppIsExitState(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::LayerState *layerState = (rive::LayerState *)ref;
        return layerState->is<rive::ExitState>();
    }

    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_LayerState_cppIsAnyState(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::LayerState *layerState = (rive::LayerState *)ref;
        return layerState->is<rive::AnyState>();
    }

    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_LayerState_cppIsEntryState(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::LayerState *layerState = (rive::LayerState *)ref;
        return layerState->is<rive::EntryState>();
    }

    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_LayerState_cppIsAnimationState(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::LayerState *layerState = (rive::LayerState *)ref;
        return layerState->is<rive::AnimationState>();
    }

    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_LayerState_cppIsBlendState(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::LayerState *layerState = (rive::LayerState *)ref;
        return layerState->is<rive::BlendState>();
    }

    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_LayerState_cppIsBlendState1D(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::LayerState *layerState = (rive::LayerState *)ref;
        return layerState->is<rive::BlendState1D>();
    }

    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_LayerState_cppIsBlendStateDirect(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::LayerState *layerState = (rive::LayerState *)ref;
        return layerState->is<rive::BlendStateDirect>();
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_AnimationState_cppAnimation(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::AnimationState *animationState = (rive::AnimationState *)ref;
        return (long)animationState->animation();
    }

#ifdef __cplusplus
}
#endif
