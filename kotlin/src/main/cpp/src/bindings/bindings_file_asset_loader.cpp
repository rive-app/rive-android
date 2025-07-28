#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/jni_file_asset_loader.hpp"

#include "rive/assets/file_asset.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif

    using namespace rive_android;

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_FileAssetLoader_constructor(
        JNIEnv* env,
        jobject ktObject)
    {
        // FileAssetLoader is now a RefCnt. Upon creation its count is 1.
        JNIFileAssetLoader* fileAssetLoader =
            new JNIFileAssetLoader(ktObject, env);
        return (jlong)fileAssetLoader;
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_FileAssetLoader_cppDelete(JNIEnv*,
                                                                jobject,
                                                                jlong ref)
    {
        JNIFileAssetLoader* fileAssetLoader =
            reinterpret_cast<JNIFileAssetLoader*>(ref);
        fileAssetLoader->unref();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_FileAssetLoader_cppRef(JNIEnv*,
                                                             jobject,
                                                             jlong ref)
    {
        JNIFileAssetLoader* fileAssetLoader =
            reinterpret_cast<JNIFileAssetLoader*>(ref);
        fileAssetLoader->ref();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_FileAssetLoader_cppSetRendererType(
        JNIEnv*,
        jobject,
        jlong resolverAddress,
        jint type)
    {
        JNIFileAssetLoader* fileAssetLoader =
            reinterpret_cast<JNIFileAssetLoader*>(resolverAddress);
        fileAssetLoader->setRendererType(static_cast<RendererType>(type));
    }
#ifdef __cplusplus
}
#endif
