#include "jni_refs.hpp"
#include "helpers/general.hpp"

// From rive-cpp
#include "file.hpp"
//
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    // FILE
    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_File_import(
        JNIEnv *env,
        jobject thisObj,
        jbyteArray bytes,
        jint length)
    {
        rive_android::setSDKVersion();
        auto file = ::import(
            (uint8_t *)env->GetByteArrayElements(bytes, NULL),
            length);
        return (jlong)file;
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_File_cppArtboard(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::File *file = (rive::File *)ref;
        return (jlong)file->artboard();
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_File_cppArtboardByName(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jstring name)
    {
        rive::File *file = (rive::File *)ref;
        return (jlong)file->artboard(
            jstring2string(env, name));
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_File_cppDelete(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        // if we're wiping the file, we really should wipe all those refs?
        rive::File *file = (rive::File *)ref;
        delete file;
    }

    JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_core_File_cppArtboardCount(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::File *file = (rive::File *)ref;

        return (jint)file->artboardCount();
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_File_cppArtboardByIndex(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jint index)
    {
        rive::File *file = (rive::File *)ref;
        return (jlong)file->artboard(index);
    }

#ifdef __cplusplus
}
#endif
