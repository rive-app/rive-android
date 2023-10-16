#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/assets/file_asset.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif

    using namespace rive_android;

    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_FileAsset_cppName(JNIEnv* env,
                                                                                  jobject thisObj,
                                                                                  jlong address)
    {
        rive::FileAsset* fileAsset = reinterpret_cast<rive::FileAsset*>(address);
        return env->NewStringUTF(fileAsset->name().c_str());
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_FileAsset_cppUniqueFilename(JNIEnv* env,
                                                                  jobject thisObj,
                                                                  jlong address)
    {
        rive::FileAsset* fileAsset = reinterpret_cast<rive::FileAsset*>(address);
        return env->NewStringUTF(fileAsset->uniqueFilename().c_str());
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_FileAsset_cppDecode(JNIEnv* env,
                                                          jobject thisObj,
                                                          jlong address,
                                                          jbyteArray assetBytes,
                                                          jint rendererTypeIndex)
    {
        rive::FileAsset* fileAsset = reinterpret_cast<rive::FileAsset*>(address);
        RendererType rendererType = static_cast<RendererType>(rendererTypeIndex);

        rive::Factory* fileFactory = GetFactory(rendererType);
        jbyte* byte_array = env->GetByteArrayElements(assetBytes, NULL);
        size_t length = JIntToSizeT(env->GetArrayLength(assetBytes));
        bool res = fileAsset->decode(
            rive::Span<const uint8_t>(reinterpret_cast<uint8_t*>(byte_array), length),
            fileFactory);
        env->ReleaseByteArrayElements(assetBytes, byte_array, JNI_ABORT);
        return res;
    }

    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_FileAsset_cppCDNUrl(JNIEnv* env,
                                                                                    jobject thisObj,
                                                                                    jlong address)
    {
        rive::FileAsset* fileAsset = reinterpret_cast<rive::FileAsset*>(address);
        std::string uuid = fileAsset->cdnUuidStr();
        if (uuid.empty())
        {
            return env->NewStringUTF("");
        }

        std::string cdnUrl = fileAsset->cdnBaseUrl();
        char lastChar = cdnUrl.back();
        if (lastChar != '/')
        {
            cdnUrl += ('/');
        }
        cdnUrl += uuid;
        return env->NewStringUTF(cdnUrl.c_str());
    }

#ifdef __cplusplus
}
#endif
