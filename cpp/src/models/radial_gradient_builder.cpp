#include "helpers/general.hpp"
#include "models/radial_gradient_builder.hpp"

// From rive-cpp
#include "math/vec2d.hpp"
//

using namespace rive_android;

void JNIRadialGradientBuilder::apply(jobject paint)
{
    int numStops = stops.size();
    JNIEnv * env = getJNIEnv();

    jintArray jcolors = env->NewIntArray(numStops);
    jfloatArray jstops = env->NewFloatArray(numStops);
    env->SetIntArrayRegion(jcolors, 0, numStops, colors.data());
    env->SetFloatArrayRegion(jstops, 0, numStops, stops.data());

    float radius = rive::Vec2D::distance(rive::Vec2D(sx, sy), rive::Vec2D(ex, ey));

    jclass tileModeClass = getTileModeClass();
    jobject clampObject = env->GetStaticObjectField(
            tileModeClass,
            getClampId());
    jclass radialGradientClass = getRadialGradientClass();

    jobject shaderObject = env->NewObject(
        radialGradientClass,
        getRadialGradientInitMethodId(),
        sx,
        sy,
        radius,
        jcolors,
        jstops,
        clampObject);

    jobject newShaderObject = env->CallObjectMethod(
        paint,
        getShaderMethodId(),
        shaderObject);
    
    env->DeleteLocalRef(jcolors);
    env->DeleteLocalRef(jstops);
    env->DeleteLocalRef(tileModeClass);
    env->DeleteLocalRef(clampObject);
    env->DeleteLocalRef(radialGradientClass);
    env->DeleteLocalRef(shaderObject);
    env->DeleteLocalRef(newShaderObject);
}