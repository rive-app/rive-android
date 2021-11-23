#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/animation/state_machine_instance.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
	using namespace rive_android;

	// ANIMATION INSTANCE
	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineInstance_constructor(
	    JNIEnv* env, jobject thisObj, jlong stateMachineRef)
	{

		auto animation = (rive::StateMachine*)stateMachineRef;

		// TODO: delete this object?
		auto stateMachineInstance = new rive::StateMachineInstance(animation);

		return (jlong)stateMachineInstance;
	}

	JNIEXPORT jboolean JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppAdvance(
	    JNIEnv* env,
	    jobject thisObj,
	    jlong ref,
	    jlong artboardRef,
	    jfloat elapsedTime)
	{
		auto stateMachineInstance = (rive::StateMachineInstance*)ref;
		auto artboard = (rive::Artboard*)artboardRef;
		return stateMachineInstance->advance(artboard, elapsedTime);
	}

	JNIEXPORT jint JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppStateChangedCount(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{
		auto stateMachineInstance = (rive::StateMachineInstance*)ref;
		return stateMachineInstance->stateChangedCount();
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppStateChangedByIndex(
	    JNIEnv* env, jobject thisObj, jlong ref, jint index)
	{
		auto stateMachineInstance = (rive::StateMachineInstance*)ref;
		return (jlong)stateMachineInstance->stateChangedByIndex(index);
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppSMIInputByIndex(
	    JNIEnv* env, jobject thisObj, jlong ref, jint index)
	{
		auto stateMachineInstance = (rive::StateMachineInstance*)ref;

		return (jlong)stateMachineInstance->input(index);
	}

	JNIEXPORT jint JNICALL
	Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppInputCount(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{
		auto stateMachineInstance = (rive::StateMachineInstance*)ref;

		return (jlong)stateMachineInstance->inputCount();
	}

#ifdef __cplusplus
}
#endif
