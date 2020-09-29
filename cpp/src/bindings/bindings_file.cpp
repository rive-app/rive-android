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
    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_File_import(
        JNIEnv *env,
        jobject thisObj,
        jbyteArray bytes,
        jint length)
    {
        ::globalJNIEnv = env;

        ::update(env);

        auto file = ::import(
            (uint8_t *)env->GetByteArrayElements(bytes, NULL),
            length);

        return (jlong)file;
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_File_nativeArtboard(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        ::globalJNIEnv = env;

        rive::File *file = (rive::File *)ref;

        return (jlong)file->artboard();
    }

#ifdef __cplusplus
}
#endif
