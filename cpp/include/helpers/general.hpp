#ifndef _RIVE_ANDROID_GENERAL_HPP_
#define _RIVE_ANDROID_GENERAL_HPP_

#include "layout.hpp"
#include <jni.h>

namespace rive_android
{
	extern JNIEnv *globalJNIEnv;
	extern jobject globalJNIObj;
	extern jobject androidCanvas;
	long import(uint8_t *bytes, jint length);
	rive::Alignment getAlignment(JNIEnv *env, jobject jalignment);
	rive::Fit getFit(JNIEnv *env, jobject jfit);

} // namespace rive_android
#endif