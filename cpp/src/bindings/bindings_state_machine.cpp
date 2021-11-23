#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/animation/state_machine.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

	// ANIMATION
	JNIEXPORT jstring JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachine_cppName(JNIEnv* env,
	                                                       jobject thisObj,
	                                                       jlong ref)
	{
		auto stateMachine = (rive::StateMachine*)ref;
		return env->NewStringUTF(stateMachine->name().c_str());
	}

	JNIEXPORT jint JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachine_cppInputCount(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{
		auto stateMachine = (rive::StateMachine*)ref;
		return (jint)stateMachine->inputCount();
	}

	JNIEXPORT jint JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachine_cppLayerCount(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{
		auto stateMachine = (rive::StateMachine*)ref;
		return (jint)stateMachine->layerCount();
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachine_cppStateMachineInputByIndex(
	    JNIEnv* env, jobject thisObj, jlong ref, jint index)
	{
		auto stateMachine = (rive::StateMachine*)ref;
		return (jlong)stateMachine->input(index);
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachine_cppStateMachineInputByName(
	    JNIEnv* env, jobject thisObj, jlong ref, jstring name)
	{
		auto stateMachine = (rive::StateMachine*)ref;

		return (jlong)stateMachine->input(
		    rive_android::jstring2string(env, name));
	}

#ifdef __cplusplus
}
#endif
