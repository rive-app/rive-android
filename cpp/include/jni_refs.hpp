#ifndef _RIVE_ANDROID_JNI_REFS_HPP_
#define _RIVE_ANDROID_JNI_REFS_HPP_

#include <jni.h>

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

} // namespace rive_android
#endif