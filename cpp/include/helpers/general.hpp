#ifndef _RIVE_ANDROID_GENERAL_HPP_
#define _RIVE_ANDROID_GENERAL_HPP_

#include "layout.hpp"
#include <jni.h>

#include <android/log.h>

#define LOG_TAG __FILE__

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)


namespace rive_android
{
	extern JNIEnv *globalJNIEnv;
	extern jobject globalJNIObj;
	extern jobject androidCanvas;
	extern int sdkVersion;
	void setSDKVersion();
	long import(uint8_t *bytes, jint length);
	rive::Alignment getAlignment(JNIEnv *env, jobject jalignment);
	rive::Fit getFit(JNIEnv *env, jobject jfit);

} // namespace rive_android
#endif