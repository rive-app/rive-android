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
        // pretty much considered the entrypoint.
        env->GetJavaVM(&::globalJavaVM);
        rive_android::setSDKVersion();
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
        env->GetJavaVM(&::globalJavaVM);

        rive::File *file = (rive::File *)ref;

        return (jlong)file->artboard();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_File_nativeDelete(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        // if we're wiping the file, we really should wipe all those refs?

        env->GetJavaVM(&::globalJavaVM);

        rive::File *file = (rive::File *)ref;
        delete file;
    }

#ifdef __cplusplus
}
#endif
