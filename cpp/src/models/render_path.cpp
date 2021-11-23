#include "models/render_path.hpp"
#include "jni_refs.hpp"
#include <android/log.h>

using namespace rive_android;

JNIRenderPath::JNIRenderPath()
{
	jclass pathClass = getPathClass();
	jobject jLocalRef =
	    getJNIEnv()->NewObject(pathClass, getPathInitMethodId());
	jObject = getJNIEnv()->NewGlobalRef(jLocalRef);
	getJNIEnv()->DeleteLocalRef(pathClass);
	getJNIEnv()->DeleteLocalRef(jLocalRef);
}

JNIRenderPath::~JNIRenderPath() { getJNIEnv()->DeleteGlobalRef(jObject); }

void JNIRenderPath::reset()
{
	getJNIEnv()->CallVoidMethod(jObject, getResetMethodId());
}

void JNIRenderPath::fillRule(rive::FillRule value)
{
	jfieldID fillTypeId;
	switch (value)
	{
		case rive::FillRule::evenOdd:
			fillTypeId = getEvenOddId();
			break;
		case rive::FillRule::nonZero:
			fillTypeId = getNonZeroId();
			break;
	}

	JNIEnv* env = getJNIEnv();
	jclass fillTypeClass = getFillTypeClass();
	jobject fillId = env->GetStaticObjectField(fillTypeClass, fillTypeId);

	env->CallVoidMethod(jObject, getSetFillTypeMethodId(), fillId);
	env->DeleteLocalRef(fillTypeClass);
	env->DeleteLocalRef(fillId);
}

void JNIRenderPath::addRenderPath(rive::RenderPath* path,
                                  const rive::Mat2D& transform)
{
	JNIEnv* env = getJNIEnv();
	jclass matrixClass = getMatrixClass();
	jobject matrix = env->NewObject(matrixClass, getMatrixInitMethodId());

	float threeDMatrix[9] = {transform.xx(),
	                         transform.yx(),
	                         transform.tx(),
	                         transform.xy(),
	                         transform.yy(),
	                         transform.ty(),
	                         0,
	                         0,
	                         1};

	jfloatArray matrixArray = env->NewFloatArray(9);
	env->SetFloatArrayRegion(matrixArray, 0, 9, threeDMatrix);

	env->CallVoidMethod(matrix, getMatrixSetValuesMethodId(), matrixArray);

	env->CallVoidMethod(jObject,
	                    getAddPathMethodId(),
	                    reinterpret_cast<JNIRenderPath*>(path)->jObject,
	                    matrix);

	env->DeleteLocalRef(matrixClass);
	env->DeleteLocalRef(matrix);
	env->DeleteLocalRef(matrixArray);
}

void JNIRenderPath::moveTo(float x, float y)
{

	getJNIEnv()->CallVoidMethod(jObject, getMoveToMethodId(), x, y);
}

void JNIRenderPath::lineTo(float x, float y)
{
	getJNIEnv()->CallVoidMethod(jObject, getLineToMethodId(), x, y);
}

void JNIRenderPath::cubicTo(
    float ox, float oy, float ix, float iy, float x, float y)
{
	getJNIEnv()->CallVoidMethod(
	    jObject, getCubicToMethodId(), ox, oy, ix, iy, x, y);
}

void JNIRenderPath::close()
{
	getJNIEnv()->CallVoidMethod(jObject, getCloseMethodId());
}