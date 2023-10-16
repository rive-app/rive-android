#include "models/jni_file_asset_loader.hpp"

namespace rive_android
{
JNIFileAssetLoader::JNIFileAssetLoader(jobject ktObject, JNIEnv* env)
{
    m_ktFileAssetLoader = env->NewGlobalRef(ktObject);
    jclass ktClass = env->GetObjectClass(ktObject);
    m_ktLoadContentsFn =
        env->GetMethodID(ktClass, "loadContents", "(Lapp/rive/runtime/kotlin/core/FileAsset;[B)Z");
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

bool JNIFileAssetLoader::loadContents(rive::FileAsset& asset, rive::Span<const uint8_t> inBandBytes)
{
    JNIEnv* env = GetJNIEnv();
    jclass fileAssetClass = env->FindClass("app/rive/runtime/kotlin/core/FileAsset");
    if (!fileAssetClass)
    {
        LOGE("JNIFileAssetLoader::loadContents() failed to find FileAsset class");
        return false;
    }

    jmethodID fileAssetConstructor = env->GetMethodID(fileAssetClass, "<init>", "(JI)V");
    if (!fileAssetConstructor)
    {
        LOGE("JNIFileAssetLoader::loadContents() failed to find FileAsset constructor");
        env->DeleteLocalRef(fileAssetClass);
        return false;
    }

    long assetAddress = reinterpret_cast<long>(&asset);
    // Renderer type must be set.
    // If not set, FileAsset constructor will throw on RendererType::None value being -1
    assert(m_rendererType != RendererType::None);
    jobject ktFileAsset = env->NewObject(fileAssetClass,
                                         fileAssetConstructor,
                                         assetAddress,
                                         static_cast<int>(m_rendererType));
    if (!ktFileAsset)
    {
        LOGE("JNIFileAssetLoader::loadContents() failed to create FileAsset");
        env->DeleteLocalRef(fileAssetClass);
        return false;
    }

    // Is inBandBytes always defined? Can it ever be a nullptr?
    jbyteArray byteArray = env->NewByteArray(rive_android::SizeTTOInt(inBandBytes.size()));
    if (!byteArray)
    {
        LOGE("JNIFileAssetLoader::loadContents() failed to allocate NewByteArray");
        return false;
    }
    env->SetByteArrayRegion(byteArray,
                            0,
                            rive_android::SizeTTOInt(inBandBytes.size()),
                            (jbyte*)inBandBytes.data());

    env->CallBooleanMethod(m_ktFileAssetLoader, m_ktLoadContentsFn, ktFileAsset, byteArray);

    env->DeleteLocalRef(byteArray);
    env->DeleteLocalRef(ktFileAsset);
    env->DeleteLocalRef(fileAssetClass);
    return true;
}
} // namespace rive_android
