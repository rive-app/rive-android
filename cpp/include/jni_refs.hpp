#ifndef _RIVE_ANDROID_JNI_REFS_HPP_
#define _RIVE_ANDROID_JNI_REFS_HPP_
#include <jni.h>

namespace rive_android
{
	extern jclass fitClass;
	extern jmethodID fitNameMethodId;

	extern jclass alignmentClass;
	extern jmethodID alignmentNameMethodId;

	extern jclass radialGradientClass;
	extern jmethodID radialGradientInitMethodId;

	extern jclass linearGradientClass;
	extern jmethodID linearGradientInitMethodId;

	extern jclass tileModeClass;
	extern jfieldID clampId;
	extern jfieldID repeatId;
	extern jfieldID mirrorId;

	extern jclass paintClass;
	extern jmethodID paintInitMethod;
	extern jmethodID setColorMethodId;
	extern jmethodID shaderMethodId;
	extern jmethodID setStyleMethodId;

	extern jclass styleClass;
	extern jfieldID fillId;
	extern jfieldID strokeId;

	extern jclass joinClass;
	extern jfieldID miterId;
	extern jfieldID roundId;
	extern jfieldID bevelId;

	extern jmethodID setStrokeWidthMethodId;
	extern jmethodID setStrokeJoinMethodId;

	extern jclass capClass;

	extern jfieldID capButtID;
	extern jfieldID capRoundId;
	extern jfieldID capSquareId;

	extern jmethodID setStrokeCapMethodId;
	extern jclass blendModeClass;

	extern jfieldID srcOver;
	extern jfieldID screen;
	extern jfieldID overlay;
	extern jfieldID darken;
	extern jfieldID lighten;
	extern jfieldID colorDodge;
	extern jfieldID colorBurn;
	extern jfieldID hardLight;
	extern jfieldID softLight;
	extern jfieldID difference;
	extern jfieldID exclusion;
	extern jfieldID multiply;
	extern jfieldID hue;
	extern jfieldID saturation;
	extern jfieldID color;
	extern jfieldID luminosity;
	extern jfieldID clear;
	extern jmethodID setBlendModeMethodId;

	extern jclass pathClass;
	extern jmethodID pathInitMethodId;
	extern jmethodID resetMethodId;
	extern jmethodID setFillTypeMethodId;

	extern jclass fillTypeClass;
	extern jfieldID evenOddId;
	extern jfieldID nonZeroId;


	extern jclass matrixClass;
	extern jmethodID matrixInitMethodId;
	extern jmethodID matrixSetValuesMethodId;
	extern jmethodID addPathMethodId;
	extern jmethodID moveToMethodId;
	extern jmethodID lineToMethodId;
	extern jmethodID cubicToMethodId;
	extern jmethodID closeMethodId;

	extern jclass riveRendererClass;
	// extern jfieldID riveCanvasFieldId;

	extern jclass androidCanvasClass;
	extern jmethodID saveMethodId;
	extern jmethodID restoreMethodId;
	extern jmethodID setMatrixMethodId;
	extern jmethodID translateMethodId;
	extern jmethodID drawPathMethodId;
	extern jmethodID clipPathMethodId;
	// extern jmethodID invalidateMethodId;

	extern jclass loopClass;

	void update(JNIEnv *env);
	void disposeRefs(JNIEnv *env);

} // namespace rive_android
#endif