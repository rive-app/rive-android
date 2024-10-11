#include "models/jni_file_asset_loader.hpp"

namespace rive_android
{
JNIFileAssetLoader::JNIFileAssetLoader(jobject ktObject, JNIEnv* env)
{
    m_ktFileAssetLoader = env->NewGlobalRef(ktObject);
    jclass ktClass = env->GetObjectClass(ktObject);
    m_ktLoadContentsFn =
        env->GetMethodID(ktClass,
                         "loadContents",
                         "(Lapp/rive/runtime/kotlin/core/FileAsset;[B)Z");
}

JNIFileAssetLoader::~JNIFileAssetLoader()
{
    JNIEnv* env = GetJNIEnv();
    if (m_ktFileAssetLoader)
    {
        env->DeleteGlobalRef(m_ktFileAssetLoader);
    }
    m_ktLoadContentsFn = nullptr;
}

bool JNIFileAssetLoader::loadContents(rive::FileAsset& asset,
                                      rive::Span<const uint8_t> inBandBytes,
                                      rive::Factory* /* unused atm */)
{
    JNIEnv* env = GetJNIEnv();
    // Renderer type must be set.
    // If not set, FileAsset constructor will throw on RendererType::None value
    // being -1
    assert(m_rendererType != RendererType::None);
    jobject ktFileAsset =
        JNIFileAssetLoader::MakeKtAsset(env, asset, m_rendererType);
    if (!ktFileAsset)
    {
        LOGE("JNIFileAssetLoader::loadContents() failed to create FileAsset");
        return false;
    }

    // Is inBandBytes always defined? Can it ever be a nullptr?
    jbyteArray byteArray =
        env->NewByteArray(rive_android::SizeTTOInt(inBandBytes.size()));
    if (!byteArray)
    {
        LOGE("JNIFileAssetLoader::loadContents() failed to allocate "
             "NewByteArray");
        return false;
    }
    env->SetByteArrayRegion(byteArray,
                            0,
                            rive_android::SizeTTOInt(inBandBytes.size()),
                            (jbyte*)inBandBytes.data());
    jboolean result = env->CallBooleanMethod(m_ktFileAssetLoader,
                                             m_ktLoadContentsFn,
                                             ktFileAsset,
                                             byteArray);

    env->DeleteLocalRef(byteArray);
    env->DeleteLocalRef(ktFileAsset);
    return result;
}
} // namespace rive_android
