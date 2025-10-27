#pragma once

#include <jni.h>
#include <android/log.h>
#include <string>
#include <sstream>

namespace rive_android
{
/**
 * A utility class to facilitate JNI calls and manage exceptions.
 *
 * This class provides helper methods for invoking JVM calls from native code.
 * After executing a JNI call, it checks if an exception has occurred, and if
 * so, rethrows it with additional context information, including the full stack
 * trace from the JVM. This enhances the clarity of error reporting, making it
 * easier to debug issues originating from Java code.
 */
class JNIExceptionHandler
{
private:
    static std::string get_exception_message(JNIEnv* env, jthrowable exception);

    static void append_throwable_message(JNIEnv* env,
                                         jthrowable throwable,
                                         std::ostringstream& errorMsg);

    static void append_stack_trace(JNIEnv* env,
                                   jthrowable throwable,
                                   jmethodID midGetStackTrace,
                                   std::ostringstream& errorMsg);

    static jthrowable get_cause(JNIEnv* env, jthrowable throwable);

    static void check_and_rethrow(JNIEnv* env);

public:
    static jobject CallObjectMethod(JNIEnv* env,
                                    jobject obj,
                                    jmethodID methodID,
                                    ...);

    static void CallVoidMethod(JNIEnv* env,
                               jobject obj,
                               jmethodID methodID,
                               ...);

    static jint CallIntMethod(JNIEnv* env,
                              jobject obj,
                              jmethodID methodID,
                              ...);

    static jboolean CallBooleanMethod(JNIEnv* env,
                                      jobject obj,
                                      jmethodID methodID,
                                      ...);

    static jobject CallStaticObjectMethod(JNIEnv* env,
                                          jclass clazz,
                                          jmethodID methodID,
                                          ...);
};
} // namespace rive_android
