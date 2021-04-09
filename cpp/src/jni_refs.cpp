#include <jni.h>

#include <android/log.h>
#include <sys/system_properties.h>
#include <stdlib.h>
#include "jni_refs.hpp"
#include "helpers/general.hpp"

namespace rive_android
{
	jclass getClass(const char *name)
	{
		return static_cast<jclass>(getJNIEnv()->FindClass(name));
	}
	jmethodID getMethodId(jclass clazz, const char *name, const char *sig)
	{
		return getJNIEnv()->GetMethodID(clazz, name, sig);
	}
	jfieldID getStaticFieldId(jclass clazz, const char *name, const char *sig)
	{
		return getJNIEnv()->GetStaticFieldID(clazz, name, sig);
	}

	jclass getFitClass()
	{
		return getClass("app/rive/runtime/kotlin/Fit");
	};
	jmethodID getFitNameMethodId()
	{
		return getMethodId(getFitClass(), "name", "()Ljava/lang/String;");
	}

	jclass getAlignmentClass()
	{
		return getClass("app/rive/runtime/kotlin/Alignment");
	}
	jmethodID getAlignmentNameMethodId()
	{
		return getMethodId(getAlignmentClass(), "name", "()Ljava/lang/String;");
	};

	jclass getRadialGradientClass()
	{
		return getClass("android/graphics/RadialGradient");
	};
	jmethodID getRadialGradientInitMethodId()
	{
		return getMethodId(getRadialGradientClass(), "<init>", "(FFF[I[FLandroid/graphics/Shader$TileMode;)V");
	};

	jclass getLinearGradientClass()
	{
		return getClass("android/graphics/LinearGradient");
	};
	jmethodID getLinearGradientInitMethodId()
	{
		return getMethodId(getLinearGradientClass(), "<init>", "(FFFF[I[FLandroid/graphics/Shader$TileMode;)V");
	};

	jclass getTileModeClass()
	{
		return getClass("android/graphics/Shader$TileMode");
	};
	jfieldID getClampId()
	{
		return getStaticFieldId(getTileModeClass(), "CLAMP", "Landroid/graphics/Shader$TileMode;");
	};
	jfieldID getRepeatId()
	{
		return getStaticFieldId(getTileModeClass(), "REPEAT", "Landroid/graphics/Shader$TileMode;");
	};
	jfieldID getMirrorId()
	{
		return getStaticFieldId(getTileModeClass(), "MIRROR", "Landroid/graphics/Shader$TileMode;");
	};

	jclass getPaintClass()
	{
		return getClass("android/graphics/Paint");
	};

	jmethodID getPaintInitMethod()
	{
		return getMethodId(getPaintClass(), "<init>", "()V");
	};
	jmethodID getSetColorMethodId()
	{
		return getMethodId(getPaintClass(), "setColor", "(I)V");
	};
	jmethodID getShaderMethodId()
	{
		return getMethodId(getPaintClass(), "setShader", "(Landroid/graphics/Shader;)Landroid/graphics/Shader;");
	};
	jmethodID getSetStyleMethodId()
	{
		return getMethodId(getPaintClass(), "setStyle", "(Landroid/graphics/Paint$Style;)V");
	};

	jclass getStyleClass()
	{
		return getClass("android/graphics/Paint$Style");
	};
	jclass getJoinClass()
	{
		return getClass("android/graphics/Paint$Join");
	};
	jmethodID getSetStrokeWidthMethodId()
	{
		return getMethodId(getPaintClass(), "setStrokeWidth", "(F)V");
	};
	jmethodID getSetStrokeJoinMethodId()
	{
		return getMethodId(getPaintClass(), "setStrokeJoin", "(Landroid/graphics/Paint$Join;)V");
	};

	jclass getCapClass()
	{
		return getClass("android/graphics/Paint$Cap");
	};

	jmethodID getSetStrokeCapMethodId()
	{
		return getMethodId(getPaintClass(), "setStrokeCap", "(Landroid/graphics/Paint$Cap;)V");
	};
	jclass getBlendModeClass()
	{

		return getClass("android/graphics/BlendMode");
	};

	jmethodID getSetBlendModeMethodId()
	{
		return getMethodId(getPaintClass(), "setBlendMode", "(Landroid/graphics/BlendMode;)V");
	};

	jclass getPathClass()
	{
		return getClass("android/graphics/Path");
	};
	jmethodID getPathInitMethodId()
	{
		return getMethodId(getPathClass(), "<init>", "()V");
	};
	jmethodID getResetMethodId()
	{
		return getMethodId(getPathClass(), "reset", "()V");
	};
	jmethodID getSetFillTypeMethodId()
	{
		return getMethodId(getPathClass(), "setFillType", "(Landroid/graphics/Path$FillType;)V");
	};

	jclass getFillTypeClass()
	{
		return getClass("android/graphics/Path$FillType");
	};
	jclass getMatrixClass()
	{
		return getClass("android/graphics/Matrix");
	};
	jmethodID getMatrixInitMethodId()
	{
		return getMethodId(getMatrixClass(), "<init>", "()V");
	};
	jmethodID getMatrixSetValuesMethodId()
	{
		return getMethodId(getMatrixClass(), "setValues", "([F)V");
	};
	jmethodID getAddPathMethodId()
	{
		return getMethodId(
			getPathClass(),
			"addPath",
			"(Landroid/graphics/Path;Landroid/graphics/Matrix;)V");
	};
	jmethodID getMoveToMethodId() { return getMethodId(getPathClass(), "moveTo", "(FF)V"); };
	jmethodID getLineToMethodId() { return getMethodId(getPathClass(), "lineTo", "(FF)V"); };
	jmethodID getCubicToMethodId() { return getMethodId(getPathClass(), "cubicTo", "(FFFFFF)V"); };
	jmethodID getCloseMethodId() { return getMethodId(getPathClass(), "close", "()V"); };

	jclass getRiveRendererClass()
	{
		return getClass("app/rive/runtime/kotlin/Renderer");
	};

	jclass getAndroidCanvasClass()
	{
		return getClass("android/graphics/Canvas");
	};
	;
	jmethodID getSaveMethodId() { return getMethodId(
		getRiveRendererClass(),
		"save",
		"()I"); };
	jmethodID getRestoreMethodId() { return getMethodId(
		getRiveRendererClass(),
		"restore",
		"()V"); };
	jmethodID getSetMatrixMethodId() { return getMethodId(
		getRiveRendererClass(),
		"setMatrix",
		"(Landroid/graphics/Matrix;)V"); };
	jmethodID getTranslateMethodId() { return getMethodId(getRiveRendererClass(), "translate", "(FF)V"); };
	jmethodID getDrawPathMethodId() { return getMethodId(
		getRiveRendererClass(),
		"drawPath",
		"(Landroid/graphics/Path;Landroid/graphics/Paint;)V"); };
	jmethodID getClipPathMethodId() { return getMethodId(
		getRiveRendererClass(),
		"clipPath",
		"(Landroid/graphics/Path;)Z"); };

	jclass getLoopClass()
	{
		return getClass("app/rive/runtime/kotlin/Loop");
	};

	jclass getPorterDuffClass()
	{
		return getClass("android/graphics/PorterDuff$Mode");
	};
	;
	jclass getPorterDuffXferModeClass()
	{
		return getClass("android/graphics/PorterDuffXfermode");
	};
	;
	jmethodID getPorterDuffXferModeInitMethodId()
	{
		return getMethodId(getPorterDuffXferModeClass(), "<init>", "(Landroid/graphics/PorterDuff$Mode;)V");
	};
	jmethodID getSetXfermodeMethodId() { return getMethodId(getPaintClass(), "setXfermode", "(Landroid/graphics/Xfermode;)Landroid/graphics/Xfermode;"); };

	jfieldID getFillId() { return getStaticFieldId(getStyleClass(), "FILL", "Landroid/graphics/Paint$Style;"); };
	jfieldID getStrokeId() { return getStaticFieldId(getStyleClass(), "STROKE", "Landroid/graphics/Paint$Style;"); };
	jfieldID getMiterId() { return getStaticFieldId(getJoinClass(), "MITER", "Landroid/graphics/Paint$Join;"); };
	jfieldID getRoundId() { return getStaticFieldId(getJoinClass(), "ROUND", "Landroid/graphics/Paint$Join;"); };
	jfieldID getBevelId() { return getStaticFieldId(getJoinClass(), "BEVEL", "Landroid/graphics/Paint$Join;"); };
	jfieldID getCapButtID() { return getStaticFieldId(getCapClass(), "BUTT", "Landroid/graphics/Paint$Cap;"); };
	jfieldID getCapRoundId() { return getStaticFieldId(getCapClass(), "ROUND", "Landroid/graphics/Paint$Cap;"); };
	jfieldID getCapSquareId() { return getStaticFieldId(getCapClass(), "SQUARE", "Landroid/graphics/Paint$Cap;"); };

	jfieldID getEvenOddId() { return getStaticFieldId(getFillTypeClass(), "EVEN_ODD", "Landroid/graphics/Path$FillType;"); };
	jfieldID getNonZeroId() { return getStaticFieldId(getFillTypeClass(), "WINDING", "Landroid/graphics/Path$FillType;"); };

	jfieldID getNoneLoopField() { return getStaticFieldId(getLoopClass(), "NONE", "Lapp/rive/runtime/kotlin/Loop;"); };
	jfieldID getOneShotLoopField() { return getStaticFieldId(getLoopClass(), "ONESHOT", "Lapp/rive/runtime/kotlin/Loop;"); };
	jfieldID getLoopLoopField() { return getStaticFieldId(getLoopClass(), "LOOP", "Lapp/rive/runtime/kotlin/Loop;"); };
	jfieldID getPingPongLoopField() { return getStaticFieldId(getLoopClass(), "PINGPONG", "Lapp/rive/runtime/kotlin/Loop;"); };

	jfieldID getSrcOver() { return getStaticFieldId(getBlendModeClass(), "SRC_OVER", "Landroid/graphics/BlendMode;"); };
	jfieldID getScreen() { return getStaticFieldId(getBlendModeClass(), "SCREEN", "Landroid/graphics/BlendMode;"); };
	jfieldID getOverlay() { return getStaticFieldId(getBlendModeClass(), "OVERLAY", "Landroid/graphics/BlendMode;"); };
	jfieldID getDarken() { return getStaticFieldId(getBlendModeClass(), "DARKEN", "Landroid/graphics/BlendMode;"); };
	jfieldID getLighten() { return getStaticFieldId(getBlendModeClass(), "LIGHTEN", "Landroid/graphics/BlendMode;"); };
	jfieldID getColorDodge() { return getStaticFieldId(getBlendModeClass(), "COLOR_DODGE", "Landroid/graphics/BlendMode;"); };
	jfieldID getColorBurn() { return getStaticFieldId(getBlendModeClass(), "COLOR_BURN", "Landroid/graphics/BlendMode;"); };
	jfieldID getHardLight() { return getStaticFieldId(getBlendModeClass(), "HARD_LIGHT", "Landroid/graphics/BlendMode;"); };
	jfieldID getSoftLight() { return getStaticFieldId(getBlendModeClass(), "SOFT_LIGHT", "Landroid/graphics/BlendMode;"); };
	jfieldID getDifference() { return getStaticFieldId(getBlendModeClass(), "DIFFERENCE", "Landroid/graphics/BlendMode;"); };
	jfieldID getExclusion() { return getStaticFieldId(getBlendModeClass(), "EXCLUSION", "Landroid/graphics/BlendMode;"); };
	jfieldID getMultiply() { return getStaticFieldId(getBlendModeClass(), "MULTIPLY", "Landroid/graphics/BlendMode;"); };
	jfieldID getHue() { return getStaticFieldId(getBlendModeClass(), "HUE", "Landroid/graphics/BlendMode;"); };
	jfieldID getSaturation() { return getStaticFieldId(getBlendModeClass(), "SATURATION", "Landroid/graphics/BlendMode;"); };
	jfieldID getColor() { return getStaticFieldId(getBlendModeClass(), "COLOR", "Landroid/graphics/BlendMode;"); };
	jfieldID getLuminosity() { return getStaticFieldId(getBlendModeClass(), "LUMINOSITY", "Landroid/graphics/BlendMode;"); };
	jfieldID getClear() { return getStaticFieldId(getBlendModeClass(), "CLEAR", "Landroid/graphics/BlendMode;"); };
	jfieldID getPdClear() { return getStaticFieldId(getPorterDuffClass(), "CLEAR", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdSrc() { return getStaticFieldId(getPorterDuffClass(), "SRC", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdDst() { return getStaticFieldId(getPorterDuffClass(), "DST", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdSrcOver() { return getStaticFieldId(getPorterDuffClass(), "SRC_OVER", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdDstOver() { return getStaticFieldId(getPorterDuffClass(), "DST_OVER", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdSrcIn() { return getStaticFieldId(getPorterDuffClass(), "SRC_IN", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdDstIn() { return getStaticFieldId(getPorterDuffClass(), "DST_IN", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdSrcOut() { return getStaticFieldId(getPorterDuffClass(), "SRC_OUT", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdDstOut() { return getStaticFieldId(getPorterDuffClass(), "DST_OUT", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdSrcAtop() { return getStaticFieldId(getPorterDuffClass(), "SRC_ATOP", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdDstAtop() { return getStaticFieldId(getPorterDuffClass(), "DST_ATOP", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdXor() { return getStaticFieldId(getPorterDuffClass(), "XOR", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdDarken() { return getStaticFieldId(getPorterDuffClass(), "DARKEN", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdLighten() { return getStaticFieldId(getPorterDuffClass(), "LIGHTEN", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdMultiply() { return getStaticFieldId(getPorterDuffClass(), "MULTIPLY", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdScreen() { return getStaticFieldId(getPorterDuffClass(), "SCREEN", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdAdd() { return getStaticFieldId(getPorterDuffClass(), "ADD", "Landroid/graphics/PorterDuff$Mode;"); };
	jfieldID getPdOverlay() { return getStaticFieldId(getPorterDuffClass(), "OVERLAY", "Landroid/graphics/PorterDuff$Mode;"); };

} // namespace rive_android
