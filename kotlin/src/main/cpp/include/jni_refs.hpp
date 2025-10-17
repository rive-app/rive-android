#ifndef _RIVE_ANDROID_JNI_REFS_HPP_
#define _RIVE_ANDROID_JNI_REFS_HPP_

#include <jni.h>
#include "rive/math/aabb.hpp"

namespace rive_android
{
extern jint ThrowRiveException(const char* message);
extern jint ThrowMalformedFileException(const char* message);
extern jint ThrowUnsupportedRuntimeVersionException(const char* message);

extern jclass GetHashMapClass();
extern jmethodID GetHashMapConstructorId();
extern jclass GetFloatClass();
extern jmethodID GetFloatConstructor();
extern jclass GetBooleanClass();
extern jmethodID GetBooleanConstructor();
extern jclass GetShortClass();
extern jmethodID GetShortConstructor();

extern jclass GetFitClass();
extern jmethodID GetFitNameMethodId();

extern jclass GetAlignmentClass();
extern jmethodID GetAlignmentNameMethodId();

extern jclass GetLoopClass();
extern jfieldID GetNoneLoopField();
extern jfieldID GetOneShotLoopField();
extern jfieldID GetLoopLoopField();
extern jfieldID GetPingPongLoopField();

extern jclass GetAdvanceResultClass();
extern jfieldID GetAdvanceResultAdvancedField();
extern jfieldID GetAdvanceResultOneShotField();
extern jfieldID GetAdvanceResultLoopField();
extern jfieldID GetAdvanceResultPingPongField();
extern jfieldID GetAdvanceResultNoneField();

extern jclass GetRiveEventReportClass();
extern jmethodID GetRiveEventReportConstructorId();

extern jclass GetPointerFClass();
extern jmethodID GetPointFInitMethod();
extern jfieldID GetXFieldId();
extern jfieldID GetYFieldId();

extern rive::AABB RectFToAABB(JNIEnv* env, jobject rectf);
extern void AABBToRectF(JNIEnv* env, const rive::AABB&, jobject rectf);

extern jclass GetRadialGradientClass();
extern jmethodID GetRadialGradientInitMethodId();

extern jclass GetLinearGradientClass();
extern jmethodID GetLinearGradientInitMethodId();

extern jclass GetTileModeClass();
extern jfieldID GetClampId();
extern jfieldID GetRepeatId();
extern jfieldID GetMirrorId();

extern jclass GetPaintClass();
extern jmethodID GetPaintInitMethod();
extern jmethodID GetSetColorMethodId();
extern jmethodID GetSetAlphaMethodId();
extern jmethodID GetSetAntiAliasMethodId();
extern jmethodID GetSetFilterBitmapMethodId();
extern jmethodID GetSetShaderMethodId();
extern jmethodID GetSetStyleMethodId();

extern jclass GetStyleClass();
extern jfieldID GetFillId();
extern jfieldID GetStrokeId();

extern jclass GetJoinClass();
extern jfieldID GetMiterId();
extern jfieldID GetRoundId();
extern jfieldID GetBevelId();

extern jmethodID GetSetStrokeWidthMethodId();
extern jmethodID GetSetStrokeJoinMethodId();

extern jclass GetCapClass();

extern jfieldID GetCapButtID();
extern jfieldID GetCapRoundId();
extern jfieldID GetCapSquareId();

extern jmethodID GetSetStrokeCapMethodId();
extern jclass GetBlendModeClass();

extern jfieldID GetSrcOver();
extern jfieldID GetScreen();
extern jfieldID GetOverlay();
extern jfieldID GetDarken();
extern jfieldID GetLighten();
extern jfieldID GetColorDodge();
extern jfieldID GetColorBurn();
extern jfieldID GetHardLight();
extern jfieldID GetSoftLight();
extern jfieldID GetDifference();
extern jfieldID GetExclusion();
extern jfieldID GetMultiply();
extern jfieldID GetHue();
extern jfieldID GetSaturation();
extern jfieldID GetColor();
extern jfieldID GetLuminosity();
extern jfieldID GetClear();
extern jmethodID GetSetBlendModeMethodId();

extern jclass GetPathClass();
extern jmethodID GetPathInitMethodId();
extern jmethodID GetResetMethodId();
extern jmethodID GetSetFillTypeMethodId();

extern jclass GetFillTypeClass();
extern jfieldID GetEvenOddId();
extern jfieldID GetNonZeroId();

extern jclass GetMatrixClass();
extern jmethodID GetMatrixInitMethodId();
extern jmethodID GetMatrixSetValuesMethodId();
extern jmethodID GetAddPathMethodId();
extern jmethodID GetMoveToMethodId();
extern jmethodID GetLineToMethodId();
extern jmethodID GetQuadToMethodId();
extern jmethodID GetCubicToMethodId();
extern jmethodID GetCloseMethodId();

extern jclass GetAndroidSurfaceClass();
extern jmethodID GetSurfaceLockCanvasMethodId();
extern jmethodID GetSurfaceHardwareCanvasMethodId();
extern jmethodID GetSurfaceUnlockCanvasAndPostMethodId();

extern jclass GetAndroidCanvasClass();
extern jclass GetAndroidCanvasVertexModeClass();
extern jmethodID GetCanvasSaveMethodId();
extern jmethodID GetCanvasRestoreMethodId();
extern jmethodID GetCanvasSetMatrixMethodId();
extern jmethodID GetCanvasConcatMatrixMethodId();
extern jmethodID GetCanvasTranslateMethodId();
extern jmethodID GetCanvasDrawPathMethodId();
extern jmethodID GetCanvasDrawCircleMethodId();
extern jmethodID GetCanvasDrawColorMethodId();
extern jmethodID GetCanvasDrawBitmapMethodId();
extern jmethodID GetCanvasDrawBitmapMeshMethodId();
extern jmethodID GetCanvasDrawVerticesMethodId();
extern jfieldID GetVertexModeTrianglesId();
extern jmethodID GetCanvasClipPathMethodId();
extern jmethodID GetCanvasWidthMethodId();
extern jmethodID GetCanvasHeightMethodId();

extern jclass GetPorterDuffClass();
extern jclass GetPorterDuffXferModeClass();
extern jmethodID GetPorterDuffXferModeInitMethodId();
extern jmethodID GetSetXfermodeMethodId();

extern jfieldID GetPdClear();
extern jfieldID GetPdSrc();
extern jfieldID GetPdDst();
extern jfieldID GetPdSrcOver();
extern jfieldID GetPdDstOver();
extern jfieldID GetPdSrcIn();
extern jfieldID GetPdDstIn();
extern jfieldID GetPdSrcOut();
extern jfieldID GetPdDstOut();
extern jfieldID GetPdSrcAtop();
extern jfieldID GetPdDstAtop();
extern jfieldID GetPdXor();
extern jfieldID GetPdDarken();
extern jfieldID GetPdLighten();
extern jfieldID GetPdMultiply();
extern jfieldID GetPdScreen();
extern jfieldID GetPdAdd();
extern jfieldID GetPdOverlay();

extern jclass GetBitmapShaderClass();
extern jmethodID GetBitmapShaderConstructor();
extern jmethodID GetBitmapSetLocalMatrixMethodId();
extern jclass GetAndroidBitmapClass();
extern jclass GetAndroidBitmapConfigClass();
extern jclass GetAndroidBitmapFactoryClass();
extern jmethodID GetCreateBitmapStaticMethodId();
extern jmethodID GetDecodeByteArrayStaticMethodId();
extern jmethodID GetBitmapWidthMethodId();
extern jmethodID GetBitmapHeightMethodId();
extern jfieldID GetARGB8888Field();
extern jmethodID GetBitmapSetPixelsMethodId();
} // namespace rive_android
#endif