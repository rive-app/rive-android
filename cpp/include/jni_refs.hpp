#ifndef _RIVE_ANDROID_JNI_REFS_HPP_
#define _RIVE_ANDROID_JNI_REFS_HPP_

#include <jni.h>
#include "rive/math/aabb.hpp"

namespace rive_android
{
extern jint throwRiveException(const char* message);
extern jint throwMalformedFileException(const char* message);
extern jint throwUnsupportedRuntimeVersionException(const char* message);
extern jclass getFitClass();
extern jmethodID getFitNameMethodId();

extern jclass getAlignmentClass();
extern jmethodID getAlignmentNameMethodId();

extern jclass getLoopClass();
extern jfieldID getNoneLoopField();
extern jfieldID getOneShotLoopField();
extern jfieldID getLoopLoopField();
extern jfieldID getPingPongLoopField();

extern jclass getPointerFClass();
extern jmethodID getPointFInitMethod();
extern jfieldID getXFieldId();
extern jfieldID getYFieldId();

extern rive::AABB rectFToAABB(JNIEnv* env, jobject rectf);
extern void aabbToRectF(JNIEnv* env, const rive::AABB&, jobject rectf);

} // namespace rive_android
#endif