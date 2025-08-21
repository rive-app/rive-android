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

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_SMIInput_cppName(JNIEnv* env,
                                                       jobject,
                                                       jlong ref)
    {

        rive::SMIInput* input = reinterpret_cast<rive::SMIInput*>(ref);
        return env->NewStringUTF(input->name().c_str());
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_SMIInput_cppIsBoolean(JNIEnv*,
                                                            jobject,
                                                            jlong ref)
    {

        rive::SMIInput* input = reinterpret_cast<rive::SMIInput*>(ref);
        return input->input()->is<rive::StateMachineBool>();
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_SMIInput_cppIsNumber(JNIEnv*,
                                                           jobject,
                                                           jlong ref)
    {

        rive::SMIInput* input = reinterpret_cast<rive::SMIInput*>(ref);
        return input->input()->is<rive::StateMachineNumber>();
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_SMIInput_cppIsTrigger(JNIEnv*,
                                                            jobject,
                                                            jlong ref)
    {

        rive::SMIInput* input = reinterpret_cast<rive::SMIInput*>(ref);
        return input->input()->is<rive::StateMachineTrigger>();
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_SMIBoolean_cppValue(JNIEnv*,
                                                          jobject,
                                                          jlong ref)
    {

        rive::SMIBool* input = reinterpret_cast<rive::SMIBool*>(ref);
        return input->value();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_SMIBoolean_cppSetValue(JNIEnv*,
                                                             jobject,
                                                             jlong ref,
                                                             jboolean newValue)
    {

        rive::SMIBool* input = reinterpret_cast<rive::SMIBool*>(ref);
        input->value(newValue);
    }

    JNIEXPORT jfloat JNICALL
    Java_app_rive_runtime_kotlin_core_SMINumber_cppValue(JNIEnv*,
                                                         jobject,
                                                         jlong ref)
    {

        rive::SMINumber* input = reinterpret_cast<rive::SMINumber*>(ref);
        return input->value();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_SMINumber_cppSetValue(JNIEnv*,
                                                            jobject,
                                                            jlong ref,
                                                            jfloat newValue)
    {

        rive::SMINumber* input = reinterpret_cast<rive::SMINumber*>(ref);
        input->value(newValue);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_SMITrigger_cppFire(JNIEnv*,
                                                         jobject,
                                                         jlong ref)
    {

        rive::SMITrigger* input = reinterpret_cast<rive::SMITrigger*>(ref);
        input->fire();
    }

#ifdef __cplusplus
}
#endif
