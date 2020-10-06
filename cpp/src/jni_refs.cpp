#include <jni.h>

#include <android/log.h>
#include <sys/system_properties.h>
#include <stdlib.h>
#include "jni_refs.hpp"
#include "helpers/general.hpp"

namespace rive_android
{
	jclass fitClass;
	jmethodID fitNameMethodId;

	jclass alignmentClass;
	jmethodID alignmentNameMethodId;

	jclass radialGradientClass;
	jmethodID radialGradientInitMethodId;

	jclass linearGradientClass;
	jmethodID linearGradientInitMethodId;

	jclass tileModeClass;
	jfieldID clampId;
	jfieldID repeatId;
	jfieldID mirrorId;

	jclass paintClass;
	jmethodID paintInitMethod;
	jmethodID setColorMethodId;
	jmethodID shaderMethodId;
	jmethodID setStyleMethodId;

	jclass styleClass;
	jfieldID fillId;
	jfieldID strokeId;

	jclass joinClass;
	jfieldID miterId;
	jfieldID roundId;
	jfieldID bevelId;

	jmethodID setStrokeWidthMethodId;
	jmethodID setStrokeJoinMethodId;

	jclass capClass;

	jfieldID capButtID;
	jfieldID capRoundId;
	jfieldID capSquareId;

	jmethodID setStrokeCapMethodId;
	jclass blendModeClass;

	jfieldID srcOver;
	jfieldID screen;
	jfieldID overlay;
	jfieldID darken;
	jfieldID lighten;
	jfieldID colorDodge;
	jfieldID colorBurn;
	jfieldID hardLight;
	jfieldID softLight;
	jfieldID difference;
	jfieldID exclusion;
	jfieldID multiply;
	jfieldID hue;
	jfieldID saturation;
	jfieldID color;
	jfieldID luminosity;
	jfieldID clear;
	jmethodID setBlendModeMethodId;

	jclass pathClass;
	jmethodID pathInitMethodId;
	jmethodID resetMethodId;
	jmethodID setFillTypeMethodId;

	jclass fillTypeClass;
	jfieldID evenOddId;
	jfieldID nonZeroId;

	jclass matrixClass;
	jmethodID matrixInitMethodId;
	jmethodID matrixSetValuesMethodId;
	jmethodID addPathMethodId;
	jmethodID moveToMethodId;
	jmethodID lineToMethodId;
	jmethodID cubicToMethodId;
	jmethodID closeMethodId;

	jclass riveRendererClass;
	// jfieldID riveCanvasFieldId;

	jclass androidCanvasClass;
	jmethodID saveMethodId;
	jmethodID restoreMethodId;
	jmethodID setMatrixMethodId;
	jmethodID translateMethodId;
	jmethodID drawPathMethodId;
	jmethodID clipPathMethodId;

	// jmethodID invalidateMethodId;

	jclass loopClass;
	jfieldID noneLoopField;
	jfieldID oneShotLoopField;
	jfieldID loopLoopField;
	jfieldID pingPongLoopField;

	jclass porterDuffClass;
	jclass porterDuffXferModeClass;
	jmethodID porterDuffXferModeInitMethodId;
	jmethodID setXfermodeMethodId;

	jfieldID pdClear;
	jfieldID pdSrc;
	jfieldID pdDst;
	jfieldID pdSrcOver;
	jfieldID pdDstOver;
	jfieldID pdSrcIn;
	jfieldID pdDstIn;
	jfieldID pdSrcOut;
	jfieldID pdDstOut;
	jfieldID pdSrcAtop;
	jfieldID pdDstAtop;
	jfieldID pdXor;
	jfieldID pdDarken;
	jfieldID pdLighten;
	jfieldID pdMultiply;
	jfieldID pdScreen;
	jfieldID pdAdd;
	jfieldID pdOverlay;

	void update(JNIEnv *env)
	{

		fitClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("app/rive/runtime/kotlin/Fit")));
		alignmentClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("app/rive/runtime/kotlin/Alignment")));
		radialGradientClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/RadialGradient")));
		linearGradientClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/LinearGradient")));
		tileModeClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/Shader$TileMode")));
		paintClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/Paint")));
		styleClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/Paint$Style")));
		joinClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/Paint$Join")));
		capClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/Paint$Cap")));
		matrixClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/Matrix")));
		riveRendererClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("app/rive/runtime/kotlin/Renderer")));
		androidCanvasClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/Canvas")));
		pathClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/Path")));
		fillTypeClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/Path$FillType")));

		fitNameMethodId = env->GetMethodID(fitClass, "name", "()Ljava/lang/String;");

		alignmentNameMethodId = env->GetMethodID(alignmentClass, "name", "()Ljava/lang/String;");

		radialGradientInitMethodId = env->GetMethodID(radialGradientClass, "<init>", "(FFF[I[FLandroid/graphics/Shader$TileMode;)V");

		linearGradientInitMethodId = env->GetMethodID(linearGradientClass, "<init>", "(FFFF[I[FLandroid/graphics/Shader$TileMode;)V");

		clampId = env->GetStaticFieldID(tileModeClass, "CLAMP", "Landroid/graphics/Shader$TileMode;");
		repeatId = env->GetStaticFieldID(tileModeClass, "REPEAT", "Landroid/graphics/Shader$TileMode;");
		mirrorId = env->GetStaticFieldID(tileModeClass, "MIRROR", "Landroid/graphics/Shader$TileMode;");

		paintInitMethod = env->GetMethodID(paintClass, "<init>", "()V");
		shaderMethodId = env->GetMethodID(paintClass, "setShader", "(Landroid/graphics/Shader;)Landroid/graphics/Shader;");
		setColorMethodId = env->GetMethodID(paintClass, "setColor", "(I)V");
		setStyleMethodId = env->GetMethodID(paintClass, "setStyle", "(Landroid/graphics/Paint$Style;)V");
		setStrokeWidthMethodId = env->GetMethodID(paintClass, "setStrokeWidth", "(F)V");
		setStrokeJoinMethodId = env->GetMethodID(paintClass, "setStrokeJoin", "(Landroid/graphics/Paint$Join;)V");
		setStrokeCapMethodId = env->GetMethodID(paintClass, "setStrokeCap", "(Landroid/graphics/Paint$Cap;)V");

		fillId = env->GetStaticFieldID(styleClass, "FILL", "Landroid/graphics/Paint$Style;");
		strokeId = env->GetStaticFieldID(styleClass, "STROKE", "Landroid/graphics/Paint$Style;");

		miterId = env->GetStaticFieldID(joinClass, "MITER", "Landroid/graphics/Paint$Join;");
		roundId = env->GetStaticFieldID(joinClass, "ROUND", "Landroid/graphics/Paint$Join;");
		bevelId = env->GetStaticFieldID(joinClass, "BEVEL", "Landroid/graphics/Paint$Join;");

		capButtID = env->GetStaticFieldID(capClass, "BUTT", "Landroid/graphics/Paint$Cap;");
		capRoundId = env->GetStaticFieldID(capClass, "ROUND", "Landroid/graphics/Paint$Cap;");
		capSquareId = env->GetStaticFieldID(capClass, "SQUARE", "Landroid/graphics/Paint$Cap;");

		evenOddId = env->GetStaticFieldID(fillTypeClass, "EVEN_ODD", "Landroid/graphics/Path$FillType;");
		nonZeroId = env->GetStaticFieldID(fillTypeClass, "WINDING", "Landroid/graphics/Path$FillType;");

		pathInitMethodId = env->GetMethodID(pathClass, "<init>", "()V");

		resetMethodId = env->GetMethodID(pathClass, "reset", "()V");
		setFillTypeMethodId = env->GetMethodID(pathClass, "setFillType", "(Landroid/graphics/Path$FillType;)V");

		matrixInitMethodId = env->GetMethodID(matrixClass, "<init>", "()V");
		matrixSetValuesMethodId = env->GetMethodID(matrixClass, "setValues", "([F)V");
		addPathMethodId = env->GetMethodID(
			pathClass,
			"addPath",
			"(Landroid/graphics/Path;Landroid/graphics/Matrix;)V");

		moveToMethodId = env->GetMethodID(pathClass, "moveTo", "(FF)V");
		lineToMethodId = env->GetMethodID(pathClass, "lineTo", "(FF)V");
		cubicToMethodId = env->GetMethodID(pathClass, "cubicTo", "(FFFFFF)V");
		closeMethodId = env->GetMethodID(pathClass, "close", "()V");

		saveMethodId = env->GetMethodID(
			riveRendererClass,
			"save",
			"()I");
		restoreMethodId = env->GetMethodID(
			riveRendererClass,
			"restore",
			"()V");
		setMatrixMethodId = env->GetMethodID(
			riveRendererClass,
			"setMatrix",
			"(Landroid/graphics/Matrix;)V");

		translateMethodId = env->GetMethodID(riveRendererClass, "translate", "(FF)V");

		drawPathMethodId = env->GetMethodID(
			riveRendererClass,
			"drawPath",
			"(Landroid/graphics/Path;Landroid/graphics/Paint;)V");
		clipPathMethodId = env->GetMethodID(
			riveRendererClass,
			"clipPath",
			"(Landroid/graphics/Path;)Z");
		// invalidateMethodId = env->GetMethodID(riveRendererClass, "invalidate", "()V");
		loopClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("app/rive/runtime/kotlin/Loop")));
		oneShotLoopField = env->GetStaticFieldID(
			loopClass, "ONESHOT", "Lapp/rive/runtime/kotlin/Loop;");

		loopLoopField = env->GetStaticFieldID(
			loopClass, "LOOP", "Lapp/rive/runtime/kotlin/Loop;");

		pingPongLoopField = env->GetStaticFieldID(
			loopClass, "PINGPONG", "Lapp/rive/runtime/kotlin/Loop;");

		setXfermodeMethodId = env->GetMethodID(paintClass, "setXfermode", "(Landroid/graphics/Xfermode;)Landroid/graphics/Xfermode;");

		if (rive_android::sdkVersion >= 29)
		{
			blendModeClass = static_cast<jclass>(
				env->NewGlobalRef(env->FindClass("android/graphics/BlendMode")));

			setBlendModeMethodId = env->GetMethodID(paintClass, "setBlendMode", "(Landroid/graphics/BlendMode;)V");

			srcOver = env->GetStaticFieldID(blendModeClass, "SRC_OVER", "Landroid/graphics/BlendMode;");
			screen = env->GetStaticFieldID(blendModeClass, "SCREEN", "Landroid/graphics/BlendMode;");
			overlay = env->GetStaticFieldID(blendModeClass, "OVERLAY", "Landroid/graphics/BlendMode;");
			darken = env->GetStaticFieldID(blendModeClass, "DARKEN", "Landroid/graphics/BlendMode;");
			lighten = env->GetStaticFieldID(blendModeClass, "LIGHTEN", "Landroid/graphics/BlendMode;");
			colorDodge = env->GetStaticFieldID(blendModeClass, "COLOR_DODGE", "Landroid/graphics/BlendMode;");
			colorBurn = env->GetStaticFieldID(blendModeClass, "COLOR_BURN", "Landroid/graphics/BlendMode;");
			hardLight = env->GetStaticFieldID(blendModeClass, "HARD_LIGHT", "Landroid/graphics/BlendMode;");
			softLight = env->GetStaticFieldID(blendModeClass, "SOFT_LIGHT", "Landroid/graphics/BlendMode;");
			difference = env->GetStaticFieldID(blendModeClass, "DIFFERENCE", "Landroid/graphics/BlendMode;");
			exclusion = env->GetStaticFieldID(blendModeClass, "EXCLUSION", "Landroid/graphics/BlendMode;");
			multiply = env->GetStaticFieldID(blendModeClass, "MULTIPLY", "Landroid/graphics/BlendMode;");
			hue = env->GetStaticFieldID(blendModeClass, "HUE", "Landroid/graphics/BlendMode;");
			saturation = env->GetStaticFieldID(blendModeClass, "SATURATION", "Landroid/graphics/BlendMode;");
			color = env->GetStaticFieldID(blendModeClass, "COLOR", "Landroid/graphics/BlendMode;");
			luminosity = env->GetStaticFieldID(blendModeClass, "LUMINOSITY", "Landroid/graphics/BlendMode;");
			clear = env->GetStaticFieldID(blendModeClass, "CLEAR", "Landroid/graphics/BlendMode;");
		}
		porterDuffClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/PorterDuff$Mode")));
		porterDuffXferModeClass = static_cast<jclass>(
			env->NewGlobalRef(env->FindClass("android/graphics/PorterDuffXfermode")));
		porterDuffXferModeInitMethodId = env->GetMethodID(porterDuffXferModeClass, "<init>", "(Landroid/graphics/PorterDuff$Mode;)V");
		pdClear = env->GetStaticFieldID(porterDuffClass, "CLEAR", "Landroid/graphics/PorterDuff$Mode;");
		pdSrc = env->GetStaticFieldID(porterDuffClass, "SRC", "Landroid/graphics/PorterDuff$Mode;");
		pdDst = env->GetStaticFieldID(porterDuffClass, "DST", "Landroid/graphics/PorterDuff$Mode;");
		pdSrcOver = env->GetStaticFieldID(porterDuffClass, "SRC_OVER", "Landroid/graphics/PorterDuff$Mode;");
		pdDstOver = env->GetStaticFieldID(porterDuffClass, "DST_OVER", "Landroid/graphics/PorterDuff$Mode;");
		pdSrcIn = env->GetStaticFieldID(porterDuffClass, "SRC_IN", "Landroid/graphics/PorterDuff$Mode;");
		pdDstIn = env->GetStaticFieldID(porterDuffClass, "DST_IN", "Landroid/graphics/PorterDuff$Mode;");
		pdSrcOut = env->GetStaticFieldID(porterDuffClass, "SRC_OUT", "Landroid/graphics/PorterDuff$Mode;");
		pdDstOut = env->GetStaticFieldID(porterDuffClass, "DST_OUT", "Landroid/graphics/PorterDuff$Mode;");
		pdSrcAtop = env->GetStaticFieldID(porterDuffClass, "SRC_ATOP", "Landroid/graphics/PorterDuff$Mode;");
		pdDstAtop = env->GetStaticFieldID(porterDuffClass, "DST_ATOP", "Landroid/graphics/PorterDuff$Mode;");
		pdXor = env->GetStaticFieldID(porterDuffClass, "XOR", "Landroid/graphics/PorterDuff$Mode;");
		pdDarken = env->GetStaticFieldID(porterDuffClass, "DARKEN", "Landroid/graphics/PorterDuff$Mode;");
		pdLighten = env->GetStaticFieldID(porterDuffClass, "LIGHTEN", "Landroid/graphics/PorterDuff$Mode;");
		pdMultiply = env->GetStaticFieldID(porterDuffClass, "MULTIPLY", "Landroid/graphics/PorterDuff$Mode;");
		pdScreen = env->GetStaticFieldID(porterDuffClass, "SCREEN", "Landroid/graphics/PorterDuff$Mode;");
		pdAdd = env->GetStaticFieldID(porterDuffClass, "ADD", "Landroid/graphics/PorterDuff$Mode;");
		pdOverlay = env->GetStaticFieldID(porterDuffClass, "OVERLAY", "Landroid/graphics/PorterDuff$Mode;");
	}

	void disposeRefs(JNIEnv *env)
	{
		env->DeleteGlobalRef(fitClass);
		env->DeleteGlobalRef(alignmentClass);
		env->DeleteGlobalRef(radialGradientClass);
		env->DeleteGlobalRef(linearGradientClass);
		env->DeleteGlobalRef(tileModeClass);
		env->DeleteGlobalRef(paintClass);
		env->DeleteGlobalRef(styleClass);
		env->DeleteGlobalRef(joinClass);
		env->DeleteGlobalRef(capClass);
		env->DeleteGlobalRef(blendModeClass);
		env->DeleteGlobalRef(matrixClass);
		env->DeleteGlobalRef(riveRendererClass);
		env->DeleteGlobalRef(androidCanvasClass);
		env->DeleteGlobalRef(pathClass);
		env->DeleteGlobalRef(fillTypeClass);
		env->DeleteGlobalRef(loopClass);
	}
} // namespace rive_android
