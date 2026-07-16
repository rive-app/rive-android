/**
 * Testing functions for JNI string helpers.
 */
#ifdef DEBUG

#include <jni.h>
#include <string>

#include "helpers/jni_string.hpp"

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_NativeStringTestHelper_cppMakeEmojiString(
        JNIEnv* env,
        jobject)
    {
        return MakeJString(env, u8"Deleting EGLThreadState! 🧨").release();
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_NativeStringTestHelper_cppMakeEmbeddedNullString(
        JNIEnv* env,
        jobject)
    {
        const std::string embeddedNull("Rive\0Android", 12);
        return MakeJString(env, embeddedNull).release();
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_NativeStringTestHelper_cppRoundTripString(
        JNIEnv* env,
        jobject,
        jstring value)
    {
        return MakeJString(env, JStringToString(env, value)).release();
    }

#ifdef __cplusplus
}
#endif

#endif // DEBUG
