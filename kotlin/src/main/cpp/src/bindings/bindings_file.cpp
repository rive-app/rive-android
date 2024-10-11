#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/file.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    // FILE
    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_File_import(JNIEnv* env,
                                                  jobject thisObj,
                                                  jbyteArray bytes,
                                                  jint length,
                                                  jint type,
                                                  jlong fileAssetLoader)
    {
        RendererType rendererType = static_cast<RendererType>(type);
        rive::FileAssetLoader* assetLoader =
            reinterpret_cast<rive::FileAssetLoader*>(fileAssetLoader);

        jbyte* byte_array = env->GetByteArrayElements(bytes, NULL);
        long file = Import(reinterpret_cast<uint8_t*>(byte_array),
                           length,
                           rendererType,
                           assetLoader);
        env->ReleaseByteArrayElements(bytes, byte_array, JNI_ABORT);
        return (jlong)file;
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppArtboardByName(JNIEnv* env,
                                                             jobject thisObj,
                                                             jlong ref,
                                                             jstring name)
    {
        auto file = reinterpret_cast<rive::File*>(ref);
        // Creates a new Artboard instance.
        return (jlong)file->artboardNamed(JStringToString(env, name)).release();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppDelete(JNIEnv* env,
                                                     jobject thisObj,
                                                     jlong ref)
    {
        // if we're wiping the file, we really should wipe all those refs?
        auto file = reinterpret_cast<rive::File*>(ref);
        delete file;
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppArtboardCount(JNIEnv* env,
                                                            jobject thisObj,
                                                            jlong ref)
    {
        auto file = reinterpret_cast<rive::File*>(ref);

        return (jint)file->artboardCount();
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppArtboardByIndex(JNIEnv* env,
                                                              jobject thisObj,
                                                              jlong ref,
                                                              jint index)
    {
        auto file = reinterpret_cast<rive::File*>(ref);
        // Creates a new Artboard instance.
        return (jlong)file->artboardAt(index).release();
    }

    JNIEXPORT
    jstring JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppArtboardNameByIndex(JNIEnv* env,
                                                                  jobject,
                                                                  jlong ref,
                                                                  jint index)
    {
        auto file = reinterpret_cast<rive::File*>(ref);

        auto artboard = file->artboard(index);
        auto name = artboard->name();
        return env->NewStringUTF(name.c_str());
    }

#ifdef __cplusplus
}
#endif
