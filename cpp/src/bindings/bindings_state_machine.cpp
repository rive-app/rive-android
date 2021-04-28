#include "jni_refs.hpp"
#include "helpers/general.hpp"

// From rive-cpp
#include "animation/state_machine.hpp"
//

#ifdef __cplusplus
extern "C"
{
#endif

    // ANIMATION
    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_StateMachine_nativeName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::StateMachine *stateMachine = (rive::StateMachine *)ref;
        return env->NewStringUTF(stateMachine->name().c_str());
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_StateMachine_nativeInputCount(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::StateMachine *stateMachine = (rive::StateMachine *)ref;
        return (jint)stateMachine->inputCount();
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_StateMachine_nativeLayerCount(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::StateMachine *stateMachine = (rive::StateMachine *)ref;
        return (jint)stateMachine->layerCount();
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_StateMachine_nativeStateMachineInputByIndex(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jint index)
    {
        rive::StateMachine *stateMachine = (rive::StateMachine *)ref;
        return (jlong)stateMachine->input(index);
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_StateMachine_nativeStateMachineInputByName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jstring name)
    {
        rive::StateMachine *stateMachine = (rive::StateMachine *)ref;

        return (jlong)stateMachine->input(
            rive_android::jstring2string(env, name));
    }

#ifdef __cplusplus
}
#endif
