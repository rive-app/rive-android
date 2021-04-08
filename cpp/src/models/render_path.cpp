#include "models/render_path.hpp"
#include "jni_refs.hpp"
#include <android/log.h>

using namespace rive_android;

JNIRenderPath::JNIRenderPath()
{
    // __android_log_print(ANDROID_LOG_INFO, __FILE__, "create path");
    jObject = getJNIEnv()->NewGlobalRef(
        getJNIEnv()->NewObject(pathClass, pathInitMethodId));
}

JNIRenderPath::~JNIRenderPath()
{
    // __android_log_print(ANDROID_LOG_INFO, __FILE__, "delete path");
    getJNIEnv()->DeleteGlobalRef(jObject);
}

void JNIRenderPath::reset()
{
    getJNIEnv()->CallVoidMethod(jObject, resetMethodId);
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

    auto fillId = getJNIEnv()->GetStaticObjectField(fillTypeClass, fillTypeId);
    getJNIEnv()->CallVoidMethod(
        jObject,
        setFillTypeMethodId,
        fillId);
}

void JNIRenderPath::addRenderPath(rive::RenderPath *path, const rive::Mat2D &transform)
{

    jobject matrix = getJNIEnv()->NewObject(
        matrixClass,
        matrixInitMethodId);

    float threeDMatrix[9] = {
        transform.xx(), transform.yx(), transform.tx(),
        transform.xy(), transform.yy(), transform.ty(),
        0, 0, 1};

    jfloatArray matrixArray = getJNIEnv()->NewFloatArray(9);
    getJNIEnv()->SetFloatArrayRegion(matrixArray, 0, 9, threeDMatrix);

    getJNIEnv()->CallVoidMethod(
        matrix,
        matrixSetValuesMethodId,
        matrixArray);

    getJNIEnv()->CallVoidMethod(
        jObject,
        addPathMethodId,
        reinterpret_cast<JNIRenderPath *>(path)->jObject,
        matrix);
}

void JNIRenderPath::moveTo(float x, float y)
{

    getJNIEnv()->CallVoidMethod(jObject, moveToMethodId, x, y);
}

void JNIRenderPath::lineTo(float x, float y)
{
    getJNIEnv()->CallVoidMethod(jObject, lineToMethodId, x, y);
}

void JNIRenderPath::cubicTo(
    float ox, float oy, float ix, float iy, float x, float y)
{
    getJNIEnv()->CallVoidMethod(jObject, cubicToMethodId, ox, oy, ix, iy, x, y);
}

void JNIRenderPath::close()
{
    getJNIEnv()->CallVoidMethod(jObject, closeMethodId);
}