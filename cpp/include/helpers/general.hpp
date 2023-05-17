#ifndef _RIVE_ANDROID_GENERAL_HPP_
#define _RIVE_ANDROID_GENERAL_HPP_

#include "rive/layout.hpp"
#include <jni.h>
#include <string>
#include <android/log.h>

// Print only on debug builds.
#if defined(DEBUG) || defined(LOG)
#define LOG_TAG (std::string(__FILE__ ":") + std::to_string(__LINE__)).c_str()
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
extern JavaVM* globalJavaVM;
// extern jobject androidCanvas;
extern int sdkVersion;
void setSDKVersion();
void logReferenceTables();
long import(uint8_t* bytes, jint length);

rive::Alignment getAlignment(JNIEnv* env, jobject jalignment);
rive::Fit getFit(JNIEnv* env, jobject jfit);
JNIEnv* getJNIEnv();

void detachThread();

std::string jstring2string(JNIEnv* env, jstring jStr);

#if defined(DEBUG) || defined(LOG)
// luigi: this redirects stderr to android log (probably want to ifdef this
// out for release)
void logThread();
void _check_egl_error(const char* file, int line);
#endif
} // namespace rive_android
#endif