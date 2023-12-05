//
// Created by Umberto Sonnino on 9/13/23.
//
#ifndef _RIVE_ANDROID_JNI_ASSET_LOADER_HPP_
#define _RIVE_ANDROID_JNI_ASSET_LOADER_HPP_

#include <jni.h>

#include "helpers/general.hpp"
#include "rive/factory.hpp"
#include "rive/file_asset_loader.hpp"

namespace rive_android
{
class JNIFileAssetLoader : public rive::FileAssetLoader
{
public:
    JNIFileAssetLoader(jobject, JNIEnv*);
    ~JNIFileAssetLoader();

    bool loadContents(rive::FileAsset&, rive::Span<const uint8_t>, rive::Factory*) override;

    void setRendererType(RendererType type)
    {
        if (type != m_rendererType)
        {
            m_rendererType = type;
        }
    }

private:
    jobject m_ktFileAssetLoader;
    jmethodID m_ktLoadContentsFn;

    RendererType m_rendererType = RendererType::None;
};
} // namespace rive_android
#endif // _RIVE_ANDROID_JNI_ASSET_LOADER_HPP_
