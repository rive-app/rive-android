#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/animation/state_machine_trigger.hpp"
#include "rive/animation/state_machine_number.hpp"
#include "rive/animation/state_machine_input.hpp"
#include "rive/animation/state_machine_bool.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

	// ANIMATION
	JNIEXPORT jstring JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineInput_cppName(JNIEnv* env,
	                                                            jobject thisObj,
	                                                            jlong ref)
	{

		rive::StateMachineInput* stateMachineInput =
		    (rive::StateMachineInput*)ref;
		return env->NewStringUTF(stateMachineInput->name().c_str());
	}

	JNIEXPORT jboolean JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineInput_cppIsBoolean(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{

		rive::StateMachineInput* stateMachineInput =
		    (rive::StateMachineInput*)ref;
		return stateMachineInput->is<rive::StateMachineBool>();
	}

	JNIEXPORT jboolean JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineInput_cppIsNumber(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{

		rive::StateMachineInput* stateMachineInput =
		    (rive::StateMachineInput*)ref;
		return stateMachineInput->is<rive::StateMachineNumber>();
	}

	JNIEXPORT jboolean JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineInput_cppIsTrigger(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{

		rive::StateMachineInput* stateMachineInput =
		    (rive::StateMachineInput*)ref;
		return stateMachineInput->is<rive::StateMachineTrigger>();
	}

	JNIEXPORT jboolean JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineBooleanInput_cppValue(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{

		rive::StateMachineBool* stateMachineBool = (rive::StateMachineBool*)ref;
		return stateMachineBool->value();
	}

	JNIEXPORT jfloat JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineNumberInput_cppValue(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{

		rive::StateMachineNumber* stateMachineNumber =
		    (rive::StateMachineNumber*)ref;
		return stateMachineNumber->value();
	}

#ifdef __cplusplus
}
#endif
