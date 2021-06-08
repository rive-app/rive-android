#include "helpers/general.hpp"
#include "models/linear_gradient_builder.hpp"

using namespace rive_android;

void JNILinearGradientBuilder::apply(jobject paint)
{
    int numStops = stops.size();
    JNIEnv * env = getJNIEnv();

    jintArray jcolors = env->NewIntArray(numStops);
    jfloatArray jstops = env->NewFloatArray(numStops);
    env->SetIntArrayRegion(jcolors, 0, numStops, colors.data());
    env->SetFloatArrayRegion(jstops, 0, numStops, stops.data());


    jclass tileModeClass = getTileModeClass();
    jobject clampObject = env->GetStaticObjectField(
            tileModeClass,
            getClampId());
    jclass linearGradientClass = getLinearGradientClass();

    jobject shaderObject = env->NewObject(
        linearGradientClass,
        getLinearGradientInitMethodId(),
        sx,
        sy,
        ex,
        ey,
        jcolors,
        jstops,
        clampObject);

    jobject newShaderObject = env->CallObjectMethod(
        paint,
        getShaderMethodId(),
        shaderObject);


    env->DeleteLocalRef(jcolors);
    env->DeleteLocalRef(jstops);
    env->DeleteLocalRef(linearGradientClass);
    env->DeleteLocalRef(tileModeClass);
    env->DeleteLocalRef(clampObject);    
    env->DeleteLocalRef(shaderObject);
    env->DeleteLocalRef(newShaderObject);
}