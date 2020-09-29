#include "models/render_path.hpp"
#include "jni_refs.hpp"
#include <android/log.h>

using namespace rive_android;

JNIRenderPath::JNIRenderPath()
{
    jObject = globalJNIEnv->NewGlobalRef(
        globalJNIEnv->NewObject(pathClass, pathInitMethodId));
}

JNIRenderPath::~JNIRenderPath()
{
    globalJNIEnv->DeleteGlobalRef(jObject);
}

void JNIRenderPath::reset()
{
    globalJNIEnv->CallVoidMethod(jObject, resetMethodId);
}

void JNIRenderPath::fillRule(rive::FillRule value)
{
    jfieldID fillTypeId;
    switch (value)
	{
		case rive::FillRule::evenOdd:
            fillTypeId = evenOddId;
			break;
		case rive::FillRule::nonZero:
            fillTypeId = nonZeroId;
			break;
	}

    auto fillId = globalJNIEnv->GetStaticObjectField(fillTypeClass, fillTypeId);
    globalJNIEnv->CallVoidMethod(
        jObject,
        setFillTypeMethodId,
        fillId);
}

void JNIRenderPath::addPath(rive::CommandPath *path, const rive::Mat2D &transform)
{

    jobject matrix = globalJNIEnv->NewObject(
        matrixClass,
        matrixInitMethodId);

    float threeDMatrix[9] = {
        transform.xx(), transform.yx(), transform.tx(),
        transform.xy(), transform.yy(), transform.ty(),
        0, 0, 1};

    jfloatArray matrixArray = globalJNIEnv->NewFloatArray(9);
    globalJNIEnv->SetFloatArrayRegion(matrixArray, 0, 9, threeDMatrix);

    globalJNIEnv->CallVoidMethod(
        matrix,
        matrixSetValuesMethodId,
        matrixArray);

    globalJNIEnv->CallVoidMethod(
        jObject,
        addPathMethodId,
        reinterpret_cast<JNIRenderPath *>(path->renderPath())->jObject,
        matrix);
}

void JNIRenderPath::moveTo(float x, float y)
{

    globalJNIEnv->CallVoidMethod(jObject, moveToMethodId, x, y);
}

void JNIRenderPath::lineTo(float x, float y)
{
    globalJNIEnv->CallVoidMethod(jObject, lineToMethodId, x, y);
}

void JNIRenderPath::cubicTo(
    float ox, float oy, float ix, float iy, float x, float y)
{
    globalJNIEnv->CallVoidMethod(jObject, cubicToMethodId, ox, oy, ix, iy, x, y);
}

void JNIRenderPath::close()
{
    globalJNIEnv->CallVoidMethod(jObject, closeMethodId);
}