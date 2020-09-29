#include "helpers/general.hpp"
#include "models/linear_gradient_builder.hpp"

using namespace rive_android;

void JNILinearGradientBuilder::apply(jobject paint)
{
    int numStops = stops.size();

    jintArray jcolors = globalJNIEnv->NewIntArray(numStops);
    jfloatArray jstops = globalJNIEnv->NewFloatArray(numStops);
    globalJNIEnv->SetIntArrayRegion(jcolors, 0, numStops, colors.data());
    globalJNIEnv->SetFloatArrayRegion(jstops, 0, numStops, stops.data());

    jobject shaderObject = globalJNIEnv->NewObject(
        linearGradientClass,
        linearGradientInitMethodId,
        sx,
        sy,
        ex,
        ey,
        jcolors,
        jstops,
        globalJNIEnv->GetStaticObjectField(
            tileModeClass,
            clampId));

    globalJNIEnv->CallObjectMethod(
        paint,
        shaderMethodId,
        shaderObject);
}