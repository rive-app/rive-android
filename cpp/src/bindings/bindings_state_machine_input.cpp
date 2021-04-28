#include "jni_refs.hpp"
#include "helpers/general.hpp"

// From rive-cpp
#include "animation/state_machine_input.hpp"
#include "animation/state_machine_bool.hpp"
#include "animation/state_machine_number.hpp"
#include "animation/state_machine_trigger.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

    // ANIMATION
    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_StateMachineInput_nativeName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::StateMachineInput *stateMachineInput = (rive::StateMachineInput *)ref;
        return env->NewStringUTF(stateMachineInput->name().c_str());
    }

    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_StateMachineInput_nativeIsBoolean(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::StateMachineInput *stateMachineInput = (rive::StateMachineInput *)ref;
        return stateMachineInput->is<rive::StateMachineBool>();
    }

    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_StateMachineInput_nativeIsNumber(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::StateMachineInput *stateMachineInput = (rive::StateMachineInput *)ref;
        return stateMachineInput->is<rive::StateMachineNumber>();
    }

    JNIEXPORT jboolean JNICALL Java_app_rive_runtime_kotlin_core_StateMachineInput_nativeIsTrigger(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {

        rive::StateMachineInput *stateMachineInput = (rive::StateMachineInput *)ref;
        return stateMachineInput->is<rive::StateMachineTrigger>();
    }

#ifdef __cplusplus
}
#endif
