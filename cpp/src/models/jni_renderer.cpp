#include "jni_refs.hpp"
#include "models/jni_renderer.hpp"
#include "models/render_path.hpp"
#include "models/render_paint.hpp"

// From rive-cpp
#include "math/mat2d.hpp"
//

using namespace rive_android;

JNIRenderer::~JNIRenderer() 
{
	// TODO: cleanup.
}

void JNIRenderer::save()
{
	globalJNIEnv->CallIntMethod(jRendererObject, saveMethodId);
}

void JNIRenderer::restore()
{
	globalJNIEnv->CallVoidMethod(jRendererObject, restoreMethodId);
}

void JNIRenderer::transform(const rive::Mat2D &transform)
{
	auto env = globalJNIEnv;

	jobject matrix = env->NewObject(matrixClass, matrixInitMethodId);

	float threeDMatrix[9] = {
		transform.xx(), transform.yx(), transform.tx(),
		transform.xy(), transform.yy(), transform.ty(),
		0, 0, 1};

	jfloatArray matrixArray = env->NewFloatArray(9);
	env->SetFloatArrayRegion(matrixArray, 0, 9, threeDMatrix);

	env->CallVoidMethod(
		matrix,
		matrixSetValuesMethodId,
		matrixArray);

	env->CallVoidMethod(
		jRendererObject,
		setMatrixMethodId,
		matrix);
	env->DeleteLocalRef(matrix);
}

void JNIRenderer::translate(float x, float y)
{
	auto env = globalJNIEnv;

	env->CallVoidMethod(
		jRendererObject, translateMethodId, x, y);
}

void JNIRenderer::drawPath(rive::RenderPath *path, rive::RenderPaint *paint)
{
	auto env = globalJNIEnv;

	env->CallVoidMethod(
		jRendererObject,
		drawPathMethodId,
		reinterpret_cast<JNIRenderPath *>(path->renderPath())->jObject,
		reinterpret_cast<JNIRenderPaint *>(paint)->jObject);
}

void JNIRenderer::clipPath(rive::RenderPath *path)
{
	auto env = globalJNIEnv;

	env->CallBooleanMethod(
		jRendererObject,
		clipPathMethodId,
		reinterpret_cast<JNIRenderPath *>(path->renderPath())->jObject);
}