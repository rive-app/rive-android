#ifndef _RIVE_ANDROID_GENERAL_HPP_
#define _RIVE_ANDROID_GENERAL_HPP_

#include "rive/layout.hpp"
#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG __FILE__

// Print only on debug builds.
#ifdef DEBUG
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#else
#define LOGE(...)
#define LOGW(...)
#define LOGD(...)
#define LOGI(...)
#endif

namespace rive_android
{
	extern JavaVM* globalJavaVM;
	extern jobject androidCanvas;
	extern int sdkVersion;
	void setSDKVersion();
	void logReferenceTables();
	long import(uint8_t* bytes, jint length);

	rive::Alignment getAlignment(JNIEnv* env, jobject jalignment);
	rive::Fit getFit(JNIEnv* env, jobject jfit);
	JNIEnv* getJNIEnv();

	std::string jstring2string(JNIEnv* env, jstring jStr);

#ifdef DEBUG
	// luigi: this redirects stderr to android log (probably want to ifdef this
	// out for release)
	void logThread();
#endif
} // namespace rive_android
#endif