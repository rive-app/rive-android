#ifndef _RIVE_ANDROID_JNI_REFS_HPP_
#define _RIVE_ANDROID_JNI_REFS_HPP_

#include <jni.h>
#include "rive/math/aabb.hpp"

namespace rive_android
{
extern jint ThrowRiveException(const char* message);
extern jint ThrowMalformedFileException(const char* message);
extern jint ThrowUnsupportedRuntimeVersionException(const char* message);
extern jclass GetFitClass();
extern jmethodID GetFitNameMethodId();

extern jclass GetAlignmentClass();
extern jmethodID GetAlignmentNameMethodId();

extern jclass GetLoopClass();
extern jfieldID GetNoneLoopField();
extern jfieldID GetOneShotLoopField();
extern jfieldID GetLoopLoopField();
extern jfieldID GetPingPongLoopField();

extern jclass GetPointerFClass();
extern jmethodID GetPointFInitMethod();
extern jfieldID GetXFieldId();
extern jfieldID GetYFieldId();

extern rive::AABB RectFToAABB(JNIEnv* env, jobject rectf);
extern void AABBToRectF(JNIEnv* env, const rive::AABB&, jobject rectf);

} // namespace rive_android
#endif