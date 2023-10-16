#ifndef _RIVE_ANDROID_GENERAL_HPP_
#define _RIVE_ANDROID_GENERAL_HPP_

#include "rive/layout.hpp"
#include "rive/factory.hpp"
#include "rive/file_asset_loader.hpp"
#include <jni.h>
#include <string>
#include <android/log.h>

// Print only on debug builds.
#if defined(DEBUG) || defined(LOG)
// CMake builds print out a lot of gibberish with this macro - comment it out for now.
// #define LOG_TAG (std::string(__FILE__ ":") + std::to_string(__LINE__)).c_str()
#define LOG_TAG "rive-android-jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define EGL_ERR_CHECK() _check_egl_error(__FILE__, __LINE__)
#else
#define LOGE(...)
#define LOGW(...)
#define LOGD(...)
#define LOGI(...)
#define EGL_ERR_CHECK()
#endif

namespace rive_android
{
enum class RendererType
{
    None = -1,
    Skia = 0,
    Rive = 1
};

extern JavaVM* g_JVM;
extern long g_sdkVersion;
void SetSDKVersion();
void LogReferenceTables();
long Import(uint8_t*, jint, RendererType = RendererType::Skia, rive::FileAssetLoader* = nullptr);

rive::Alignment GetAlignment(JNIEnv*, jobject);
rive::Fit GetFit(JNIEnv*, jobject);
rive::Factory* GetFactory(RendererType);
JNIEnv* GetJNIEnv();

void DetachThread();

std::string JStringToString(JNIEnv*, jstring);
int SizeTTOInt(size_t);
size_t JIntToSizeT(jint);

#if defined(DEBUG) || defined(LOG)
// luigi: this redirects stderr to android log (probably want to ifdef this
// out for release)
[[noreturn]] void LogThread();
void _check_egl_error(const char*, int);
#endif
} // namespace rive_android
#endif
