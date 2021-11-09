#include "jni_refs.hpp"
#include "models/jni_renderer.hpp"
#include "rive/math/mat2d.hpp"

using namespace rive_android;

bool JNIRenderer::antialias = true;

JNIRenderer::~JNIRenderer()
{
	getJNIEnv()->DeleteGlobalRef(jRendererObject);
}

void JNIRenderer::save()
{
	getJNIEnv()->CallIntMethod(jRendererObject, getSaveMethodId());
}

void JNIRenderer::restore()
{
	getJNIEnv()->CallVoidMethod(jRendererObject, getRestoreMethodId());
}

void JNIRenderer::transform(const rive::Mat2D &transform)
{
	JNIEnv *env = getJNIEnv();
	jclass matrixClass = getMatrixClass();
	jobject matrix = env->NewObject(matrixClass, getMatrixInitMethodId());

	float threeDMatrix[9] = {
			transform.xx(), transform.yx(), transform.tx(),
			transform.xy(), transform.yy(), transform.ty(),
			0, 0, 1};

	jfloatArray matrixArray = env->NewFloatArray(9);
	env->SetFloatArrayRegion(matrixArray, 0, 9, threeDMatrix);

	env->CallVoidMethod(
			matrix,
			getMatrixSetValuesMethodId(),
			matrixArray);

	env->CallVoidMethod(
			jRendererObject,
			getSetMatrixMethodId(),
			matrix);

	env->DeleteLocalRef(matrixClass);
	env->DeleteLocalRef(matrixArray);
	env->DeleteLocalRef(matrix);
}

void JNIRenderer::translate(float x, float y)
{
	getJNIEnv()->CallVoidMethod(
			jRendererObject, getTranslateMethodId(), x, y);
}

void JNIRenderer::drawPath(rive::RenderPath *path, rive::RenderPaint *paint)
{
	getJNIEnv()->CallVoidMethod(
			jRendererObject,
			getDrawPathMethodId(),
			reinterpret_cast<JNIRenderPath *>(path->renderPath())->jObject,
			reinterpret_cast<JNIRenderPaint *>(paint)->jObject);
}

void JNIRenderer::clipPath(rive::RenderPath *path)
{
	getJNIEnv()->CallBooleanMethod(
			jRendererObject,
			getClipPathMethodId(),
			reinterpret_cast<JNIRenderPath *>(path->renderPath())->jObject);
}