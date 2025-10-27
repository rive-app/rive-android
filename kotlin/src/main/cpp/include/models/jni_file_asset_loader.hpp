#pragma once

#include <jni.h>

#include "helpers/general.hpp"
#include "rive/factory.hpp"
#include "rive/file_asset_loader.hpp"
#include "rive/assets/image_asset.hpp"
#include "rive/assets/font_asset.hpp"
#include "rive/assets/audio_asset.hpp"

namespace rive_android
{
class JNIFileAssetLoader : public rive::FileAssetLoader
{
public:
    JNIFileAssetLoader(jobject, JNIEnv*);
    ~JNIFileAssetLoader() override;

    bool loadContents(rive::FileAsset&,
                      rive::Span<const uint8_t>,
                      rive::Factory*) override;

    void setRendererType(RendererType type)
    {
        if (type != m_rendererType)
        {
            m_rendererType = type;
        }
    }

    static jobject MakeKtAsset(JNIEnv* env,
                               rive::FileAsset& asset,
                               RendererType rendererType)
    {
        jclass assetClass = nullptr;
        if (asset.is<rive::ImageAsset>())
        {
            assetClass =
                env->FindClass("app/rive/runtime/kotlin/core/ImageAsset");
        }
        else if (asset.is<rive::FontAsset>())
        {
            assetClass =
                env->FindClass("app/rive/runtime/kotlin/core/FontAsset");
        }
        else if (asset.is<rive::AudioAsset>())
        {
            assetClass =
                env->FindClass("app/rive/runtime/kotlin/core/AudioAsset");
        }
        else
        {
            LOGW("Trying to make unknown file asset type %d", asset.typeKey);
        }

        if (!assetClass)
        {
            LOGE("JNIFileAssetLoader::MakeKtAsset() failed to find FileAsset "
                 "class");
            return nullptr;
        }

        jmethodID fileAssetConstructor =
            env->GetMethodID(assetClass, "<init>", "(JI)V");
        if (!fileAssetConstructor)
        {
            LOGE("JNIFileAssetLoader::MakeKtAsset() failed to find FileAsset "
                 "constructor");
            env->DeleteLocalRef(assetClass);
            return nullptr;
        }

        // Renderer type must be set.
        // If not set, FileAsset constructor will throw on RendererType::None
        // value being -1
        jobject ktFileAsset = env->NewObject(assetClass,
                                             fileAssetConstructor,
                                             reinterpret_cast<jlong>(&asset),
                                             static_cast<int>(rendererType));
        if (!ktFileAsset)
        {
            LOGE(
                "JNIFileAssetLoader::MakeKtAsset() failed to create FileAsset");
            env->DeleteLocalRef(assetClass);
            return nullptr;
        }
        return ktFileAsset;
    }

private:
    jobject m_ktFileAssetLoader = nullptr;
    jmethodID m_ktLoadContentsFn;

    RendererType m_rendererType = RendererType::None;
};
} // namespace rive_android
