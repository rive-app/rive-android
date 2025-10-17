#include "jni_refs.hpp"
#include "helpers/android_factories.hpp"
#include "helpers/general.hpp"
#include "helpers/image_decode.hpp"

#include "rive/assets/image_asset.hpp"
#include "rive/simple_array.hpp"
#include "rive/assets/font_asset.hpp"
#include "rive/assets/audio_asset.hpp"

#include <jni.h>

extern "C"
{
    using namespace rive_android;

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_FileAsset_cppName(JNIEnv* env,
                                                        jobject,
                                                        jlong address)
    {
        auto* fileAsset = reinterpret_cast<rive::FileAsset*>(address);
        return env->NewStringUTF(fileAsset->name().c_str());
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_FileAsset_cppUniqueFilename(JNIEnv* env,
                                                                  jobject,
                                                                  jlong address)
    {
        auto* fileAsset = reinterpret_cast<rive::FileAsset*>(address);
        return env->NewStringUTF(fileAsset->uniqueFilename().c_str());
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_FileAsset_cppDecode(
        JNIEnv* env,
        jobject,
        jlong ref,
        jbyteArray assetBytes,
        jint rendererTypeIndex)
    {
        auto* fileAsset = reinterpret_cast<rive::FileAsset*>(ref);
        auto rendererType = static_cast<RendererType>(rendererTypeIndex);

        auto* fileFactory = GetFactory(rendererType);
        auto* jAssetBytes = env->GetByteArrayElements(assetBytes, nullptr);
        auto length = JIntToSizeT(env->GetArrayLength(assetBytes));

        // Turn into a SimpleArray so audio files can steal the bytes if they
        // want/need to.
        rive::SimpleArray<uint8_t> bytesToDecode(
            reinterpret_cast<uint8_t*>(jAssetBytes),
            length);
        auto res = fileAsset->decode(bytesToDecode, fileFactory);
        env->ReleaseByteArrayElements(assetBytes, jAssetBytes, JNI_ABORT);
        return res;
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_FileAsset_cppCDNUrl(JNIEnv* env,
                                                          jobject,
                                                          jlong ref)
    {
        auto* fileAsset = reinterpret_cast<rive::FileAsset*>(ref);
        auto uuid = fileAsset->cdnUuidStr();
        if (uuid.empty())
        {
            return env->NewStringUTF("");
        }

        auto cdnUrl = fileAsset->cdnBaseUrl();
        auto lastChar = cdnUrl.back();
        if (lastChar != '/')
        {
            cdnUrl += ('/');
        }
        cdnUrl += uuid;
        return env->NewStringUTF(cdnUrl.c_str());
    }

    /** == Images ==  */
    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ImageAsset_cppSetRenderImage(
        JNIEnv*,
        jobject,
        jlong imageAssetAddress,
        jlong renderImageAddress)
    {
        auto* imageAsset =
            reinterpret_cast<rive::ImageAsset*>(imageAssetAddress);
        auto* renderImage =
            reinterpret_cast<rive::RenderImage*>(renderImageAddress);
        imageAsset->renderImage(rive::ref_rcp(renderImage));
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ImageAsset_cppGetRenderImage(
        JNIEnv*,
        jobject,
        jlong imageAssetAddress)
    {
        auto* imageAsset =
            reinterpret_cast<rive::ImageAsset*>(imageAssetAddress);
        return reinterpret_cast<jlong>(imageAsset->renderImage());
    }

    JNIEXPORT jfloat JNICALL
    Java_app_rive_runtime_kotlin_core_ImageAsset_cppImageAssetWidth(JNIEnv*,
                                                                    jobject,
                                                                    jlong ref)
    {
        auto image = reinterpret_cast<rive::ImageAsset*>(ref);
        return image->width();
    }

    JNIEXPORT jfloat JNICALL
    Java_app_rive_runtime_kotlin_core_ImageAsset_cppImageAssetHeight(JNIEnv*,
                                                                     jobject,
                                                                     jlong ref)
    {
        auto image = reinterpret_cast<rive::ImageAsset*>(ref);
        return image->height();
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_RiveRenderImage_00024Companion_cppFromRGBABytes(
        JNIEnv* env,
        jobject,
        jbyteArray jBytes,
        jint jWidth,
        jint jHeight,
        jint jRendererTypeIdx,
        jboolean jPremultiplied)
    {
        const auto count = static_cast<size_t>(env->GetArrayLength(jBytes));
        const auto expected =
            static_cast<size_t>(jWidth) * static_cast<size_t>(jHeight) * 4u;
        if (jWidth <= 0 || jHeight <= 0 || count != expected)
        {
            LOGE("RiveRenderImage::cppFromRGBABytes - Invalid args.");
            return 0;
        }

        auto rendererType =
            static_cast<rive_android::RendererType>(jRendererTypeIdx);
        auto width = static_cast<uint32_t>(jWidth);
        auto height = static_cast<uint32_t>(jHeight);
        auto* buffer = env->GetByteArrayElements(jBytes, nullptr);
        const auto* rgba = reinterpret_cast<const uint8_t*>(buffer);

        auto renderImage =
            (rendererType == RendererType::Rive)
                ? renderImageFromRGBABytesRive(width,
                                               height,
                                               rgba,
                                               jPremultiplied == JNI_TRUE)
                : renderImageFromRGBABytesCanvas(width,
                                                 height,
                                                 rgba,
                                                 jPremultiplied == JNI_TRUE);
        env->ReleaseByteArrayElements(jBytes, buffer, JNI_ABORT);
        return reinterpret_cast<jlong>(renderImage.release());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_RiveRenderImage_00024Companion_cppFromARGBInts(
        JNIEnv* env,
        jobject,
        jintArray jPixelArray,
        jint jWidth,
        jint jHeight,
        jint jRendererTypeIdx,
        jboolean jPremultiplied)
    {
        const auto count = env->GetArrayLength(jPixelArray);
        const auto expected =
            static_cast<size_t>(jWidth) * static_cast<size_t>(jHeight);
        if (jWidth <= 0 || jHeight <= 0 ||
            static_cast<size_t>(count) != expected)
        {
            LOGE("RiveRenderImage::cppFromARGBInts - Invalid args.");
            return 0;
        }
        auto* jColors = env->GetIntArrayElements(jPixelArray, nullptr);
        auto rendererType =
            static_cast<rive_android::RendererType>(jRendererTypeIdx);
        auto renderImage = (rendererType == RendererType::Rive)
                               ? rive_android::renderImageFromARGBIntsRive(
                                     static_cast<uint32_t>(jWidth),
                                     static_cast<uint32_t>(jHeight),
                                     reinterpret_cast<const uint32_t*>(jColors),
                                     jPremultiplied == JNI_TRUE)
                               : rive_android::renderImageFromARGBIntsCanvas(
                                     static_cast<uint32_t>(jWidth),
                                     static_cast<uint32_t>(jHeight),
                                     reinterpret_cast<const uint32_t*>(jColors),
                                     jPremultiplied == JNI_TRUE);
        env->ReleaseIntArrayElements(jPixelArray, jColors, JNI_ABORT);
        return reinterpret_cast<jlong>(renderImage.release());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_RiveRenderImage_00024Companion_cppFromBitmapRive(
        JNIEnv*,
        jobject,
        jobject jBitmap,
        jboolean jPremultiplied)
    {
        auto premultiplied = jPremultiplied == JNI_TRUE;
        auto renderImage = renderImageFromBitmapRive(jBitmap, premultiplied);
        return reinterpret_cast<jlong>(renderImage.release());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_RiveRenderImage_00024Companion_cppFromBitmapCanvas(
        JNIEnv*,
        jobject,
        jobject jBitmap)
    {
        auto renderImage = rive_android::renderImageFromBitmapCanvas(jBitmap);
        return reinterpret_cast<jlong>(renderImage.release());
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_RiveRenderImage_cppDelete(JNIEnv*,
                                                                jobject,
                                                                jlong ref)
    {
        releaseAsset<rive::RenderImage>(ref);
    }

    /** == Fonts ==  */
    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_FontAsset_cppSetFont(
        JNIEnv*,
        jobject,
        jlong fontAssetAddress,
        jlong fontAddress)
    {
        auto* fontAsset = reinterpret_cast<rive::FontAsset*>(fontAssetAddress);
        auto* font = reinterpret_cast<rive::Font*>(fontAddress);
        fontAsset->font(rive::ref_rcp(font));
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_FontAsset_cppGetFont(JNIEnv*,
                                                           jobject,
                                                           jlong ref)
    {
        auto* fontAsset = reinterpret_cast<rive::FontAsset*>(ref);
        return reinterpret_cast<jlong>(fontAsset->font().get());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_RiveFont_00024Companion_cppMakeFont(
        JNIEnv* env,
        jobject,
        jbyteArray byteArray,
        jint rendererTypeIdx)
    {
        auto asset = decodeAsset<rive::Font>(env, byteArray, rendererTypeIdx);
        // Manage refcount manually from Kotlin
        return reinterpret_cast<jlong>(asset.release());
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_RiveFont_cppDelete(JNIEnv*,
                                                         jobject,
                                                         jlong ref)
    {
        releaseAsset<rive::Font>(ref);
    }

    /** == Audio ==  */
    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_AudioAsset_cppSetAudio(JNIEnv*,
                                                             jobject,
                                                             jlong assetAddress,
                                                             jlong audioAddress)
    {
        auto* asset = reinterpret_cast<rive::AudioAsset*>(assetAddress);
        auto* source = reinterpret_cast<rive::AudioSource*>(audioAddress);
        asset->audioSource(rive::ref_rcp(source));
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_AudioAsset_cppGetAudio(JNIEnv*,
                                                             jobject,
                                                             jlong ref)
    {
        auto* asset = reinterpret_cast<rive::AudioAsset*>(ref);
        return reinterpret_cast<jlong>(asset->audioSource().get());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_RiveAudio_00024Companion_cppMakeAudio(
        JNIEnv* env,
        jobject,
        jbyteArray byteArray,
        jint rendererTypeIdx)
    {
        auto asset =
            decodeAsset<rive::AudioSource>(env, byteArray, rendererTypeIdx);
        // Manage refcount manually from Kotlin
        return reinterpret_cast<jlong>(asset.release());
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_RiveAudio_cppDelete(JNIEnv*,
                                                          jobject,
                                                          jlong ref)
    {
        releaseAsset<rive::AudioSource>(ref);
    }
}
