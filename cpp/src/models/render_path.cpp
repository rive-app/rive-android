#include "models/render_path.hpp"
#include "jni_refs.hpp"
#include <android/log.h>

using namespace rive_android;

JNIRenderPath::JNIRenderPath()
{
    jObject = getJNIEnv()->NewGlobalRef(
        getJNIEnv()->NewObject(getPathClass(), getPathInitMethodId()));
}

JNIRenderPath::~JNIRenderPath()
{
    getJNIEnv()->DeleteGlobalRef(jObject);
}

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

    auto fillId = getJNIEnv()->GetStaticObjectField(getFillTypeClass(), fillTypeId);
    getJNIEnv()->CallVoidMethod(
        jObject,
        getSetFillTypeMethodId(),
        fillId);
}

void JNIRenderPath::addRenderPath(rive::RenderPath *path, const rive::Mat2D &transform)
{

    jobject matrix = getJNIEnv()->NewObject(
        getMatrixClass(),
        getMatrixInitMethodId());

    float threeDMatrix[9] = {
        transform.xx(), transform.yx(), transform.tx(),
        transform.xy(), transform.yy(), transform.ty(),
        0, 0, 1};

    jfloatArray matrixArray = getJNIEnv()->NewFloatArray(9);
    getJNIEnv()->SetFloatArrayRegion(matrixArray, 0, 9, threeDMatrix);

    getJNIEnv()->CallVoidMethod(
        matrix,
        getMatrixSetValuesMethodId(),
        matrixArray);

    getJNIEnv()->CallVoidMethod(
        jObject,
        getAddPathMethodId(),
        reinterpret_cast<JNIRenderPath *>(path)->jObject,
        matrix);
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
    getJNIEnv()->CallVoidMethod(jObject, getCubicToMethodId(), ox, oy, ix, iy, x, y);
}

void JNIRenderPath::close()
{
    getJNIEnv()->CallVoidMethod(jObject, getCloseMethodId());
}