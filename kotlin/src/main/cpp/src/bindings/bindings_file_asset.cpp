#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "helpers/android_factories.hpp"
#include "rive/assets/image_asset.hpp"
#include "rive/simple_array.hpp"
#include "rive/assets/font_asset.hpp"
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

        // Turn into a SimpleArray so audio files can steal the bytes if they
        // want/need to.
        rive::SimpleArray<uint8_t> bytesToDecode(reinterpret_cast<uint8_t*>(byte_array), length);
        bool res = fileAsset->decode(bytesToDecode, fileFactory);
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

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ImageAsset_cppSetRenderImage(JNIEnv* env,
                                                                   jobject thisObj,
                                                                   jlong imageAssetAddress,
                                                                   jlong renderImageAddress)
    {
        rive::ImageAsset* imageAsset = reinterpret_cast<rive::ImageAsset*>(imageAssetAddress);
        rive::RenderImage* renderImage = reinterpret_cast<rive::RenderImage*>(renderImageAddress);
        imageAsset->renderImage(rive::ref_rcp(renderImage));
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ImageAsset_cppGetRenderImage(JNIEnv* env,
                                                                   jobject thisObj,
                                                                   jlong imageAssetAddress)
    {
        rive::ImageAsset* imageAsset = reinterpret_cast<rive::ImageAsset*>(imageAssetAddress);
        return reinterpret_cast<jlong>(imageAsset->renderImage());
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_FontAsset_cppSetFont(JNIEnv* env,
                                                           jobject thisObj,
                                                           jlong fontAssetAddress,
                                                           jlong fontAddress)
    {
        rive::FontAsset* fontAsset = reinterpret_cast<rive::FontAsset*>(fontAssetAddress);
        rive::Font* font = reinterpret_cast<rive::Font*>(fontAddress);
        fontAsset->font(rive::ref_rcp(font));
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_FontAsset_cppGetFont(JNIEnv* env,
                                                           jobject thisObj,
                                                           jlong fontAssetAddress)
    {
        rive::FontAsset* fontAsset = reinterpret_cast<rive::FontAsset*>(fontAssetAddress);
        return reinterpret_cast<jlong>(fontAsset->font().get());
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_RiveRenderImage_cppDelete(JNIEnv* env,
                                                                jobject imgClass,
                                                                jlong address)
    {
        releaseAsset<rive::RenderImage>(address);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_RiveRenderImage_00024Companion_cppMakeImage(
        JNIEnv* env,
        jobject,
        jbyteArray byteArray,
        jint rendererTypeIdx)
    {
        auto asset = decodeAsset<rive::RenderImage>(env, byteArray, rendererTypeIdx);
        // Calling `[release()]` transfers ownership of this object to the caller.
        return reinterpret_cast<jlong>(asset.release());
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_RiveFont_cppDelete(JNIEnv* env,
                                                                                jobject imgClass,
                                                                                jlong address)
    {
        releaseAsset<rive::Font>(address);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_RiveFont_00024Companion_cppMakeFont(JNIEnv* env,
                                                                          jobject,
                                                                          jbyteArray byteArray,
                                                                          jint rendererTypeIdx)
    {
        auto asset = decodeAsset<rive::Font>(env, byteArray, rendererTypeIdx);
        // Calling `[release()]` transfers ownership of this object to the caller.
        return reinterpret_cast<jlong>(asset.release());
    }

#ifdef __cplusplus
}
#endif
