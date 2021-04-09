#ifndef _RIVE_ANDROID_JNI_REFS_HPP_
#define _RIVE_ANDROID_JNI_REFS_HPP_
#include <jni.h>

namespace rive_android
{
	extern jclass getFitClass();
	extern jmethodID getFitNameMethodId();

	extern jclass getAlignmentClass();
	extern jmethodID getAlignmentNameMethodId();

	extern jclass getRadialGradientClass();
	extern jmethodID getRadialGradientInitMethodId();

	extern jclass getLinearGradientClass();
	extern jmethodID getLinearGradientInitMethodId();

	extern jclass getTileModeClass();
	extern jfieldID getClampId();
	extern jfieldID getRepeatId();
	extern jfieldID getMirrorId();

	extern jclass getPaintClass();
	extern jmethodID getPaintInitMethod();
	extern jmethodID getSetColorMethodId();
	extern jmethodID getShaderMethodId();
	extern jmethodID getSetStyleMethodId();

	extern jclass getStyleClass();
	extern jfieldID getFillId();
	extern jfieldID getStrokeId();

	extern jclass getJoinClass();
	extern jfieldID getMiterId();
	extern jfieldID getRoundId();
	extern jfieldID getBevelId();

	extern jmethodID getSetStrokeWidthMethodId();
	extern jmethodID getSetStrokeJoinMethodId();

	extern jclass getCapClass();

	extern jfieldID getCapButtID();
	extern jfieldID getCapRoundId();
	extern jfieldID getCapSquareId();

	extern jmethodID getSetStrokeCapMethodId();
	extern jclass getBlendModeClass();

	extern jfieldID getSrcOver();
	extern jfieldID getScreen();
	extern jfieldID getOverlay();
	extern jfieldID getDarken();
	extern jfieldID getLighten();
	extern jfieldID getColorDodge();
	extern jfieldID getColorBurn();
	extern jfieldID getHardLight();
	extern jfieldID getSoftLight();
	extern jfieldID getDifference();
	extern jfieldID getExclusion();
	extern jfieldID getMultiply();
	extern jfieldID getHue();
	extern jfieldID getSaturation();
	extern jfieldID getColor();
	extern jfieldID getLuminosity();
	extern jfieldID getClear();
	extern jmethodID getSetBlendModeMethodId();

	extern jclass getPathClass();
	extern jmethodID getPathInitMethodId();
	extern jmethodID getResetMethodId();
	extern jmethodID getSetFillTypeMethodId();

	extern jclass getFillTypeClass();
	extern jfieldID getEvenOddId();
	extern jfieldID getNonZeroId();

	extern jclass getMatrixClass();
	extern jmethodID getMatrixInitMethodId();
	extern jmethodID getMatrixSetValuesMethodId();
	extern jmethodID getAddPathMethodId();
	extern jmethodID getMoveToMethodId();
	extern jmethodID getLineToMethodId();
	extern jmethodID getCubicToMethodId();
	extern jmethodID getCloseMethodId();

	extern jclass getRiveRendererClass();

	extern jclass getAndroidCanvasClass();
	extern jmethodID getSaveMethodId();
	extern jmethodID getRestoreMethodId();
	extern jmethodID getSetMatrixMethodId();
	extern jmethodID getTranslateMethodId();
	extern jmethodID getDrawPathMethodId();
	extern jmethodID getClipPathMethodId();

	extern jclass getLoopClass();
	extern jfieldID getNoneLoopField();
	extern jfieldID getOneShotLoopField();
	extern jfieldID getLoopLoopField();
	extern jfieldID getPingPongLoopField();

	extern jclass getPorterDuffClass();
	extern jclass getPorterDuffXferModeClass();
	extern jmethodID getPorterDuffXferModeInitMethodId();
	extern jmethodID getSetXfermodeMethodId();

	extern jfieldID getPdClear();
	extern jfieldID getPdSrc();
	extern jfieldID getPdDst();
	extern jfieldID getPdSrcOver();
	extern jfieldID getPdDstOver();
	extern jfieldID getPdSrcIn();
	extern jfieldID getPdDstIn();
	extern jfieldID getPdSrcOut();
	extern jfieldID getPdDstOut();
	extern jfieldID getPdSrcAtop();
	extern jfieldID getPdDstAtop();
	extern jfieldID getPdXor();
	extern jfieldID getPdDarken();
	extern jfieldID getPdLighten();
	extern jfieldID getPdMultiply();
	extern jfieldID getPdScreen();
	extern jfieldID getPdAdd();
	extern jfieldID getPdOverlay();

} // namespace rive_android
#endif