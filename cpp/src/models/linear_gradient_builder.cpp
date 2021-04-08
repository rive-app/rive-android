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
        linearGradientClass,
        linearGradientInitMethodId,
        sx,
        sy,
        ex,
        ey,
        jcolors,
        jstops,
        getJNIEnv()->GetStaticObjectField(
            tileModeClass,
            clampId));

    getJNIEnv()->CallObjectMethod(
        paint,
        shaderMethodId,
        shaderObject);
}