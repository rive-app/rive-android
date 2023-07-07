#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/animation/state_machine_trigger.hpp"
#include "rive/animation/state_machine_number.hpp"
#include "rive/animation/state_machine_input.hpp"
#include "rive/animation/state_machine_input_instance.hpp"
#include "rive/animation/state_machine_bool.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_SMIInput_cppName(JNIEnv* env,
                                                                                 jobject thisObj,
                                                                                 jlong ref)
    {

        rive::SMIInput* input = (rive::SMIInput*)ref;
        return env->NewStringUTF(input->name().c_str());
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_SMIInput_cppIsBoolean(JNIEnv* env, jobject thisObj, jlong ref)
    {

        rive::SMIInput* input = (rive::SMIInput*)ref;
        return input->input()->is<rive::StateMachineBool>();
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_SMIInput_cppIsNumber(JNIEnv* env, jobject thisObj, jlong ref)
    {

        rive::SMIInput* input = (rive::SMIInput*)ref;
        return input->input()->is<rive::StateMachineNumber>();
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_SMIInput_cppIsTrigger(JNIEnv* env, jobject thisObj, jlong ref)
    {

        rive::SMIInput* input = (rive::SMIInput*)ref;
        return input->input()->is<rive::StateMachineTrigger>();
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_SMIBoolean_cppValue(JNIEnv* env, jobject thisObj, jlong ref)
    {

        rive::SMIBool* input = (rive::SMIBool*)ref;
        return input->value();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_SMIBoolean_cppSetValue(JNIEnv* env,
                                                             jobject thisObj,
                                                             jlong ref,
                                                             jboolean newValue)
    {

        rive::SMIBool* input = (rive::SMIBool*)ref;
        input->value(newValue);
    }

    JNIEXPORT jfloat JNICALL Java_app_rive_runtime_kotlin_core_SMINumber_cppValue(JNIEnv* env,
                                                                                  jobject thisObj,
                                                                                  jlong ref)
    {

        rive::SMINumber* input = (rive::SMINumber*)ref;
        return input->value();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_SMINumber_cppSetValue(JNIEnv* env,
                                                                                   jobject thisObj,
                                                                                   jlong ref,
                                                                                   jfloat newValue)
    {

        rive::SMINumber* input = (rive::SMINumber*)ref;
        input->value(newValue);
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_SMITrigger_cppFire(JNIEnv* env,
                                                                                jobject thisObj,
                                                                                jlong ref)
    {

        rive::SMITrigger* input = (rive::SMITrigger*)ref;
        input->fire();
    }

#ifdef __cplusplus
}
#endif
