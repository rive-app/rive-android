#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/animation/state_machine_instance.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppAdvance(JNIEnv* env,
                                                                      jobject thisObj,
                                                                      jlong ref,
                                                                      jfloat elapsedTime)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        return stateMachineInstance->advance(elapsedTime);
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppStateChangedCount(JNIEnv* env,
                                                                                jobject thisObj,
                                                                                jlong ref)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        return SizeTTOInt(stateMachineInstance->stateChangedCount());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppStateChangedByIndex(JNIEnv* env,
                                                                                  jobject thisObj,
                                                                                  jlong ref,
                                                                                  jint index)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        return (jlong)stateMachineInstance->stateChangedByIndex(index);
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppReportedEventCount(JNIEnv* env,
                                                                                 jobject thisObj,
                                                                                 jlong ref)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        return SizeTTOInt(stateMachineInstance->reportedEventCount());
    }

    JNIEXPORT jobject JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppReportedEventAt(JNIEnv* env,
                                                                              jobject thisObj,
                                                                              jlong ref,
                                                                              jint index)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        const rive::EventReport eventReport = stateMachineInstance->reportedEventAt(index);

        return env->NewObject(GetRiveEventReportClass(),
                              GetRiveEventReportConstructorId(),
                              (jlong)eventReport.event(),
                              eventReport.secondsDelay());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppSMIInputByIndex(JNIEnv* env,
                                                                              jobject thisObj,
                                                                              jlong ref,
                                                                              jint index)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);

        return (jlong)stateMachineInstance->input(index);
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppInputCount(JNIEnv* env,
                                                                         jobject thisObj,
                                                                         jlong ref)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        return SizeTTOInt(stateMachineInstance->inputCount());
    }

    // ANIMATION
    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppName(JNIEnv* env,
                                                                   jobject thisObj,
                                                                   jlong ref)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        return env->NewStringUTF(stateMachineInstance->stateMachine()->name().c_str());
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppLayerCount(JNIEnv* env,
                                                                         jobject thisObj,
                                                                         jlong ref)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        return (jint)stateMachineInstance->stateMachine()->layerCount();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppPointerDown(JNIEnv* env,
                                                                          jobject thisObj,
                                                                          jlong ref,
                                                                          jfloat x,
                                                                          jfloat y)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        stateMachineInstance->pointerDown(rive::Vec2D(x, y));
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppPointerMove(JNIEnv* env,
                                                                          jobject thisObj,
                                                                          jlong ref,
                                                                          jfloat x,
                                                                          jfloat y)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        stateMachineInstance->pointerMove(rive::Vec2D(x, y));
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppPointerUp(JNIEnv* env,
                                                                        jobject thisObj,
                                                                        jlong ref,
                                                                        jfloat x,
                                                                        jfloat y)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        stateMachineInstance->pointerUp(rive::Vec2D(x, y));
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_StateMachineInstance_cppDelete(JNIEnv*, jobject, jlong ref)
    {
        auto stateMachineInstance = reinterpret_cast<rive::StateMachineInstance*>(ref);
        delete stateMachineInstance;
    }

#ifdef __cplusplus
}
#endif
