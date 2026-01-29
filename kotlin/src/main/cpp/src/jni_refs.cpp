#include <jni.h>
#include <android/log.h>
#include <sys/system_properties.h>
#include <cstdlib>
#include "jni_refs.hpp"
#include "helpers/general.hpp"

namespace rive_android
{
jclass GetClass(const char* name) { return GetJNIEnv()->FindClass(name); }
jmethodID GetMethodId(jclass clazz, const char* name, const char* sig)
{
    JNIEnv* env = GetJNIEnv();
    jmethodID output = env->GetMethodID(clazz, name, sig);
    env->DeleteLocalRef(clazz);
    return output;
}
jmethodID GetStaticMethodId(jclass clazz, const char* name, const char* sig)
{
    JNIEnv* env = GetJNIEnv();
    jmethodID output = env->GetStaticMethodID(clazz, name, sig);
    env->DeleteLocalRef(clazz);
    return output;
}

jfieldID GetStaticFieldId(jclass clazz, const char* name, const char* sig)
{
    JNIEnv* env = GetJNIEnv();
    jfieldID output = env->GetStaticFieldID(clazz, name, sig);
    env->DeleteLocalRef(clazz);
    return output;
}

jfieldID GetFieldId(jclass clazz, const char* name, const char* sig)
{
    JNIEnv* env = GetJNIEnv();
    jfieldID output = env->GetFieldID(clazz, name, sig);
    env->DeleteLocalRef(clazz);
    return output;
}

jint ThrowRiveException(const char* message)
{
    jclass exClass =
        GetClass("app/rive/runtime/kotlin/core/errors/RiveException");
    return GetJNIEnv()->ThrowNew(exClass, message);
}
jint ThrowMalformedFileException(const char* message)
{
    jclass exClass =
        GetClass("app/rive/runtime/kotlin/core/errors/MalformedFileException");
    return GetJNIEnv()->ThrowNew(exClass, message);
}
jint ThrowUnsupportedRuntimeVersionException(const char* message)
{
    jclass exClass = GetClass("app/rive/runtime/kotlin/core/errors/"
                              "UnsupportedRuntimeVersionException");
    return GetJNIEnv()->ThrowNew(exClass, message);
}

jclass GetHashMapClass() { return GetClass("java/util/HashMap"); }
jmethodID GetHashMapConstructorId()
{
    return GetMethodId(GetHashMapClass(), "<init>", "()V");
}

jclass GetFloatClass() { return GetClass("java/lang/Float"); }
jmethodID GetFloatConstructor()
{
    return GetMethodId(GetFloatClass(), "<init>", "(F)V");
}

jclass GetBooleanClass() { return GetClass("java/lang/Boolean"); }
jmethodID GetBooleanConstructor()
{
    return GetMethodId(GetBooleanClass(), "<init>", "(Z)V");
}

jclass GetShortClass() { return GetClass("java/lang/Short"); }
jmethodID GetShortConstructor()
{
    return GetMethodId(GetShortClass(), "<init>", "(S)V");
}

jclass GetFitClass() { return GetClass("app/rive/runtime/kotlin/core/Fit"); }
jmethodID GetFitNameMethodId()
{
    return GetMethodId(GetFitClass(), "name", "()Ljava/lang/String;");
}

jclass GetAlignmentClass()
{
    return GetClass("app/rive/runtime/kotlin/core/Alignment");
}
jmethodID GetAlignmentNameMethodId()
{
    return GetMethodId(GetAlignmentClass(), "name", "()Ljava/lang/String;");
}

jclass GetLoopClass() { return GetClass("app/rive/runtime/kotlin/core/Loop"); }

jfieldID GetNoneLoopField()
{
    return GetStaticFieldId(GetLoopClass(),
                            "NONE",
                            "Lapp/rive/runtime/kotlin/core/Loop;");
}
jfieldID GetOneShotLoopField()
{
    return GetStaticFieldId(GetLoopClass(),
                            "ONESHOT",
                            "Lapp/rive/runtime/kotlin/core/Loop;");
}
jfieldID GetLoopLoopField()
{
    return GetStaticFieldId(GetLoopClass(),
                            "LOOP",
                            "Lapp/rive/runtime/kotlin/core/Loop;");
}
jfieldID GetPingPongLoopField()
{
    return GetStaticFieldId(GetLoopClass(),
                            "PINGPONG",
                            "Lapp/rive/runtime/kotlin/core/Loop;");
}

jclass GetAdvanceResultClass()
{
    return GetClass("app/rive/runtime/kotlin/core/AdvanceResult");
}

jfieldID GetAdvanceResultAdvancedField()
{
    return GetStaticFieldId(GetAdvanceResultClass(),
                            "ADVANCED",
                            "Lapp/rive/runtime/kotlin/core/AdvanceResult;");
}
jfieldID GetAdvanceResultOneShotField()
{
    return GetStaticFieldId(GetAdvanceResultClass(),
                            "ONESHOT",
                            "Lapp/rive/runtime/kotlin/core/AdvanceResult;");
}
jfieldID GetAdvanceResultLoopField()
{
    return GetStaticFieldId(GetAdvanceResultClass(),
                            "LOOP",
                            "Lapp/rive/runtime/kotlin/core/AdvanceResult;");
}
jfieldID GetAdvanceResultPingPongField()
{
    return GetStaticFieldId(GetAdvanceResultClass(),
                            "PINGPONG",
                            "Lapp/rive/runtime/kotlin/core/AdvanceResult;");
}
jfieldID GetAdvanceResultNoneField()
{
    return GetStaticFieldId(GetAdvanceResultClass(),
                            "NONE",
                            "Lapp/rive/runtime/kotlin/core/AdvanceResult;");
}

jclass GetRiveEventReportClass()
{
    return GetClass("app/rive/runtime/kotlin/core/RiveEventReport");
}
jmethodID GetRiveEventReportConstructorId()
{
    return GetMethodId(GetRiveEventReportClass(), "<init>", "(JF)V");
}

jclass GetPointerFClass() { return GetClass("android/graphics/PointF"); }

jfieldID GetXFieldId() { return GetFieldId(GetPointerFClass(), "x", "F"); }

jfieldID GetYFieldId() { return GetFieldId(GetPointerFClass(), "y", "F"); }

jmethodID GetPointFInitMethod()
{
    return GetMethodId(GetPointerFClass(), "<init>", "(FF)V");
}

static const char* AABBFieldNames[] = {"left", "top", "right", "bottom"};

rive::AABB RectFToAABB(JNIEnv* env, jobject rectf)
{
    auto cls = env->FindClass("android/graphics/RectF");
    float values[4];
    for (int i = 0; i < 4; ++i)
    {
        values[i] =
            env->GetFloatField(rectf,
                               env->GetFieldID(cls, AABBFieldNames[i], "F"));
    }
    env->DeleteLocalRef(cls);
    return {values[0], values[1], values[2], values[3]};
}

void AABBToRectF(JNIEnv* env, const rive::AABB& aabb, jobject rectf)
{
    auto cls = env->FindClass("android/graphics/RectF");
    const float values[4] = {aabb.left(),
                             aabb.top(),
                             aabb.right(),
                             aabb.bottom()};
    for (int i = 0; i < 4; ++i)
    {
        env->SetFloatField(rectf,
                           env->GetFieldID(cls, AABBFieldNames[i], "F"),
                           values[i]);
    }
    env->DeleteLocalRef(cls);
}

jclass GetRadialGradientClass()
{
    return GetClass("android/graphics/RadialGradient");
}
jmethodID GetRadialGradientInitMethodId()
{
    return GetMethodId(GetRadialGradientClass(),
                       "<init>",
                       "(FFF[I[FLandroid/graphics/Shader$TileMode;)V");
}

jclass GetLinearGradientClass()
{
    return GetClass("android/graphics/LinearGradient");
}
jmethodID GetLinearGradientInitMethodId()
{
    return GetMethodId(GetLinearGradientClass(),
                       "<init>",
                       "(FFFF[I[FLandroid/graphics/Shader$TileMode;)V");
}

jclass GetTileModeClass()
{
    return GetClass("android/graphics/Shader$TileMode");
}
jfieldID GetClampId()
{
    return GetStaticFieldId(GetTileModeClass(),
                            "CLAMP",
                            "Landroid/graphics/Shader$TileMode;");
}

jclass GetPaintClass() { return GetClass("android/graphics/Paint"); }

jmethodID GetPaintInitMethod()
{
    return GetMethodId(GetPaintClass(), "<init>", "()V");
}
jmethodID GetSetColorMethodId()
{
    return GetMethodId(GetPaintClass(), "setColor", "(I)V");
}
jmethodID GetSetAlphaMethodId()
{
    return GetMethodId(GetPaintClass(), "setAlpha", "(I)V");
}
jmethodID GetSetAntiAliasMethodId()
{
    return GetMethodId(GetPaintClass(), "setAntiAlias", "(Z)V");
}
jmethodID GetSetFilterBitmapMethodId()
{
    return GetMethodId(GetPaintClass(), "setFilterBitmap", "(Z)V");
}
jmethodID GetSetShaderMethodId()
{
    return GetMethodId(GetPaintClass(),
                       "setShader",
                       "(Landroid/graphics/Shader;)Landroid/graphics/Shader;");
}
jmethodID GetSetStyleMethodId()
{
    return GetMethodId(GetPaintClass(),
                       "setStyle",
                       "(Landroid/graphics/Paint$Style;)V");
}

jclass GetStyleClass() { return GetClass("android/graphics/Paint$Style"); }
jclass GetJoinClass() { return GetClass("android/graphics/Paint$Join"); }
jmethodID GetSetStrokeWidthMethodId()
{
    return GetMethodId(GetPaintClass(), "setStrokeWidth", "(F)V");
}
jmethodID GetSetStrokeJoinMethodId()
{
    return GetMethodId(GetPaintClass(),
                       "setStrokeJoin",
                       "(Landroid/graphics/Paint$Join;)V");
}

jclass GetCapClass() { return GetClass("android/graphics/Paint$Cap"); }

jmethodID GetSetStrokeCapMethodId()
{
    return GetMethodId(GetPaintClass(),
                       "setStrokeCap",
                       "(Landroid/graphics/Paint$Cap;)V");
}
jclass GetBlendModeClass() { return GetClass("android/graphics/BlendMode"); }

jmethodID GetSetBlendModeMethodId()
{
    return GetMethodId(GetPaintClass(),
                       "setBlendMode",
                       "(Landroid/graphics/BlendMode;)V");
}

jclass GetPathClass() { return GetClass("android/graphics/Path"); }
jmethodID GetPathInitMethodId()
{
    return GetMethodId(GetPathClass(), "<init>", "()V");
}
jmethodID GetResetMethodId()
{
    return GetMethodId(GetPathClass(), "reset", "()V");
}
jmethodID GetSetFillTypeMethodId()
{
    return GetMethodId(GetPathClass(),
                       "setFillType",
                       "(Landroid/graphics/Path$FillType;)V");
}

jclass GetFillTypeClass() { return GetClass("android/graphics/Path$FillType"); }
jclass GetMatrixClass() { return GetClass("android/graphics/Matrix"); }
jmethodID GetMatrixInitMethodId()
{
    return GetMethodId(GetMatrixClass(), "<init>", "()V");
}
jmethodID GetMatrixSetValuesMethodId()
{
    return GetMethodId(GetMatrixClass(), "setValues", "([F)V");
}
jmethodID GetAddPathMethodId()
{
    return GetMethodId(GetPathClass(),
                       "addPath",
                       "(Landroid/graphics/Path;Landroid/graphics/Matrix;)V");
}
jmethodID GetMoveToMethodId()
{
    return GetMethodId(GetPathClass(), "moveTo", "(FF)V");
}
jmethodID GetLineToMethodId()
{
    return GetMethodId(GetPathClass(), "lineTo", "(FF)V");
}
jmethodID GetCubicToMethodId()
{
    return GetMethodId(GetPathClass(), "cubicTo", "(FFFFFF)V");
}
jmethodID GetCloseMethodId()
{
    return GetMethodId(GetPathClass(), "close", "()V");
}

jclass GetAndroidSurfaceClass() { return GetClass("android/view/Surface"); }
jmethodID GetSurfaceLockCanvasMethodId()
{
    return GetMethodId(GetAndroidSurfaceClass(),
                       "lockCanvas",
                       "(Landroid/graphics/Rect;)Landroid/graphics/Canvas;");
}
jmethodID GetSurfaceUnlockCanvasAndPostMethodId()
{
    return GetMethodId(GetAndroidSurfaceClass(),
                       "unlockCanvasAndPost",
                       "(Landroid/graphics/Canvas;)V");
}

jclass GetAndroidCanvasClass() { return GetClass("android/graphics/Canvas"); }
jclass GetAndroidCanvasVertexModeClass()
{
    return GetClass("android/graphics/Canvas$VertexMode");
}
jmethodID GetCanvasSaveMethodId()
{
    return GetMethodId(GetAndroidCanvasClass(), "save", "()I");
}
jmethodID GetCanvasRestoreMethodId()
{
    return GetMethodId(GetAndroidCanvasClass(), "restore", "()V");
}
jmethodID GetCanvasConcatMatrixMethodId()
{
    return GetMethodId(GetAndroidCanvasClass(),
                       "concat",
                       "(Landroid/graphics/Matrix;)V");
}
jmethodID GetCanvasDrawPathMethodId()
{
    return GetMethodId(GetAndroidCanvasClass(),
                       "drawPath",
                       "(Landroid/graphics/Path;Landroid/graphics/Paint;)V");
}
jmethodID GetCanvasDrawColorMethodId()
{
    return GetMethodId(GetAndroidCanvasClass(),
                       "drawColor",
                       "(ILandroid/graphics/PorterDuff$Mode;)V");
}
jmethodID GetCanvasDrawBitmapMethodId()
{
    return GetMethodId(
        GetAndroidCanvasClass(),
        "drawBitmap",
        "(Landroid/graphics/Bitmap;FFLandroid/graphics/Paint;)V");
}
jmethodID GetCanvasDrawVerticesMethodId()
{
    /** Kotlin signature:
     * drawVertices (Canvas.VertexMode mode,
                int vertexCount,
                float[] verts,
                int vertOffset,
                float[] texs,
                int texOffset,
                int[] colors,
                int colorOffset,
                short[] indices,
                int indexOffset,
                int indexCount,
                Paint paint)
     */
    return GetMethodId(
        GetAndroidCanvasClass(),
        "drawVertices",
        "(Landroid/graphics/Canvas$VertexMode;I[FI[FI[II[SIILandroid/graphics/"
        "Paint;)V");
}
jfieldID GetVertexModeTrianglesId()
{
    return GetStaticFieldId(GetAndroidCanvasVertexModeClass(),
                            "TRIANGLES",
                            "Landroid/graphics/Canvas$VertexMode;");
}
jmethodID GetCanvasClipPathMethodId()
{
    return GetMethodId(GetAndroidCanvasClass(),
                       "clipPath",
                       "(Landroid/graphics/Path;)Z");
}
jmethodID GetCanvasWidthMethodId()
{
    return GetMethodId(GetAndroidCanvasClass(), "getWidth", "()I");
}
jmethodID GetCanvasHeightMethodId()
{
    return GetMethodId(GetAndroidCanvasClass(), "getHeight", "()I");
}

jclass GetPorterDuffClass()
{
    return GetClass("android/graphics/PorterDuff$Mode");
}

jclass GetPorterDuffXferModeClass()
{
    return GetClass("android/graphics/PorterDuffXfermode");
}

jmethodID GetPorterDuffXferModeInitMethodId()
{
    return GetMethodId(GetPorterDuffXferModeClass(),
                       "<init>",
                       "(Landroid/graphics/PorterDuff$Mode;)V");
}
jmethodID GetSetXfermodeMethodId()
{
    return GetMethodId(
        GetPaintClass(),
        "setXfermode",
        "(Landroid/graphics/Xfermode;)Landroid/graphics/Xfermode;");
}

jfieldID GetFillId()
{
    return GetStaticFieldId(GetStyleClass(),
                            "FILL",
                            "Landroid/graphics/Paint$Style;");
}
jfieldID GetStrokeId()
{
    return GetStaticFieldId(GetStyleClass(),
                            "STROKE",
                            "Landroid/graphics/Paint$Style;");
}
jfieldID GetMiterId()
{
    return GetStaticFieldId(GetJoinClass(),
                            "MITER",
                            "Landroid/graphics/Paint$Join;");
}
jfieldID GetRoundId()
{
    return GetStaticFieldId(GetJoinClass(),
                            "ROUND",
                            "Landroid/graphics/Paint$Join;");
}
jfieldID GetBevelId()
{
    return GetStaticFieldId(GetJoinClass(),
                            "BEVEL",
                            "Landroid/graphics/Paint$Join;");
}
jfieldID GetCapButtID()
{
    return GetStaticFieldId(GetCapClass(),
                            "BUTT",
                            "Landroid/graphics/Paint$Cap;");
}
jfieldID GetCapRoundId()
{
    return GetStaticFieldId(GetCapClass(),
                            "ROUND",
                            "Landroid/graphics/Paint$Cap;");
}
jfieldID GetCapSquareId()
{
    return GetStaticFieldId(GetCapClass(),
                            "SQUARE",
                            "Landroid/graphics/Paint$Cap;");
}

jfieldID GetEvenOddId()
{
    return GetStaticFieldId(GetFillTypeClass(),
                            "EVEN_ODD",
                            "Landroid/graphics/Path$FillType;");
}
jfieldID GetNonZeroId()
{
    return GetStaticFieldId(GetFillTypeClass(),
                            "WINDING",
                            "Landroid/graphics/Path$FillType;");
}

jfieldID GetSrcOver()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "SRC_OVER",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetScreen()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "SCREEN",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetOverlay()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "OVERLAY",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetDarken()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "DARKEN",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetLighten()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "LIGHTEN",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetColorDodge()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "COLOR_DODGE",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetColorBurn()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "COLOR_BURN",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetHardLight()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "HARD_LIGHT",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetSoftLight()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "SOFT_LIGHT",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetDifference()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "DIFFERENCE",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetExclusion()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "EXCLUSION",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetMultiply()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "MULTIPLY",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetHue()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "HUE",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetSaturation()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "SATURATION",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetColor()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "COLOR",
                            "Landroid/graphics/BlendMode;");
}
jfieldID GetLuminosity()
{
    return GetStaticFieldId(GetBlendModeClass(),
                            "LUMINOSITY",
                            "Landroid/graphics/BlendMode;");
}

jfieldID GetPdClear()
{
    return GetStaticFieldId(GetPorterDuffClass(),
                            "CLEAR",
                            "Landroid/graphics/PorterDuff$Mode;");
}
jfieldID GetPdSrcOver()
{
    return GetStaticFieldId(GetPorterDuffClass(),
                            "SRC_OVER",
                            "Landroid/graphics/PorterDuff$Mode;");
}
jfieldID GetPdDarken()
{
    return GetStaticFieldId(GetPorterDuffClass(),
                            "DARKEN",
                            "Landroid/graphics/PorterDuff$Mode;");
}
jfieldID GetPdLighten()
{
    return GetStaticFieldId(GetPorterDuffClass(),
                            "LIGHTEN",
                            "Landroid/graphics/PorterDuff$Mode;");
}
jfieldID GetPdMultiply()
{
    return GetStaticFieldId(GetPorterDuffClass(),
                            "MULTIPLY",
                            "Landroid/graphics/PorterDuff$Mode;");
}
jfieldID GetPdScreen()
{
    return GetStaticFieldId(GetPorterDuffClass(),
                            "SCREEN",
                            "Landroid/graphics/PorterDuff$Mode;");
}
jfieldID GetPdOverlay()
{
    return GetStaticFieldId(GetPorterDuffClass(),
                            "OVERLAY",
                            "Landroid/graphics/PorterDuff$Mode;");
}

jclass GetBitmapShaderClass()
{
    return GetClass("android/graphics/BitmapShader");
}
jmethodID GetBitmapShaderConstructor()
{
    /**
     * Kotlin signature:
     * BitmapShader(Bitmap bitmap, Shader.TileMode tileX, Shader.TileMode tileY)
     */
    return GetMethodId(
        GetBitmapShaderClass(),
        "<init>",
        "(Landroid/graphics/Bitmap;Landroid/graphics/Shader$TileMode;Landroid/"
        "graphics/Shader$TileMode;)V");
}

jclass GetColorMatrixClass()
{
    return GetClass("android/graphics/ColorMatrix");
}
jmethodID GetColorMatrixInitMethodId()
{
    return GetMethodId(GetColorMatrixClass(), "<init>", "()V");
}
jmethodID GetColorMatrixSetMethodId()
{
    /** Kotlin signature: void set(float[] src) */
    return GetMethodId(GetColorMatrixClass(), "set", "([F)V");
}
jclass GetColorMatrixColorFilterClass()
{
    return GetClass("android/graphics/ColorMatrixColorFilter");
}
jmethodID GetColorMatrixColorFilterInitMethodId()
{
    /**
     * Kotlin signature:
     * ColorMatrixColorFilter(ColorMatrix matrix)
     */
    return GetMethodId(GetColorMatrixColorFilterClass(),
                       "<init>",
                       "(Landroid/graphics/ColorMatrix;)V");
}
jmethodID GetSetColorFilterMethodId()
{
    /**
     * Kotlin signature:
     * ColorFilter setColorFilter(ColorFilter filter)
     */
    return GetMethodId(
        GetPaintClass(),
        "setColorFilter",
        "(Landroid/graphics/ColorFilter;)Landroid/graphics/ColorFilter;");
}

jclass GetAndroidBitmapClass() { return GetClass("android/graphics/Bitmap"); }
jclass GetAndroidBitmapConfigClass()
{
    return GetClass("android/graphics/Bitmap$Config");
}
jclass GetAndroidBitmapFactoryClass()
{
    return GetClass("android/graphics/BitmapFactory");
}
jmethodID GetCreateBitmapStaticMethodId()
{
    /**
     * Kotlin signature:
     * static Bitmap createBitmap (int width,
     *  int height,
     *  Bitmap.Config config)
     */
    return GetStaticMethodId(
        GetAndroidBitmapClass(),
        "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
}
jmethodID GetDecodeByteArrayStaticMethodId()
{
    /**
     * Kotlin signature:
     * static Bitmap decodeByteArray (byte[] data,
     *   int offset,
     *   int length)
     */
    return GetStaticMethodId(GetAndroidBitmapFactoryClass(),
                             "decodeByteArray",
                             "([BII)Landroid/graphics/Bitmap;");
}
jmethodID GetBitmapWidthMethodId()
{
    /** Kotlin signature: int getWidth () */
    return GetMethodId(GetAndroidBitmapClass(), "getWidth", "()I");
}
jmethodID GetBitmapHeightMethodId()
{
    /** Kotlin signature: int getHeight () */
    return GetMethodId(GetAndroidBitmapClass(), "getHeight", "()I");
}
jfieldID GetARGB8888Field()
{
    return GetStaticFieldId(GetAndroidBitmapConfigClass(),
                            "ARGB_8888",
                            "Landroid/graphics/Bitmap$Config;");
}

jmethodID GetBitmapSetPixelsMethodId()
{
    // void setPixels (int[] pixels, int offset, int stride, int x, int y, int
    // width, int height)
    return GetMethodId(GetAndroidBitmapClass(), "setPixels", "([IIIIIII)V");
}

} // namespace rive_android
