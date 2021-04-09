#include "helpers/general.hpp"
#include "models/linear_gradient_builder.hpp"

using namespace rive_android;

void JNILinearGradientBuilder::apply(jobject paint)
{
    int numStops = stops.size();

    jintArray jcolors = getJNIEnv()->NewIntArray(numStops);
    jfloatArray jstops = getJNIEnv()->NewFloatArray(numStops);
    getJNIEnv()->SetIntArrayRegion(jcolors, 0, numStops, colors.data());
    getJNIEnv()->SetFloatArrayRegion(jstops, 0, numStops, stops.data());

    jobject shaderObject = getJNIEnv()->NewObject(
        getLinearGradientClass(),
        getLinearGradientInitMethodId(),
        sx,
        sy,
        ex,
        ey,
        jcolors,
        jstops,
        getJNIEnv()->GetStaticObjectField(
            getTileModeClass(),
            getClampId()));

    getJNIEnv()->CallObjectMethod(
        paint,
        getShaderMethodId(),
        shaderObject);
}