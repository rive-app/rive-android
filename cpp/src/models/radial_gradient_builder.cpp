#include "helpers/general.hpp"
#include "models/radial_gradient_builder.hpp"

// From rive-cpp
#include "math/vec2d.hpp"
//

using namespace rive_android;

void JNIRadialGradientBuilder::apply(jobject paint)
{
    int numStops = stops.size();

    jintArray jcolors = getJNIEnv()->NewIntArray(numStops);
    jfloatArray jstops = getJNIEnv()->NewFloatArray(numStops);
    getJNIEnv()->SetIntArrayRegion(jcolors, 0, numStops, colors.data());
    getJNIEnv()->SetFloatArrayRegion(jstops, 0, numStops, stops.data());

    float radius = rive::Vec2D::distance(rive::Vec2D(sx, sy), rive::Vec2D(ex, ey));

    jobject shaderObject = getJNIEnv()->NewObject(
        getRadialGradientClass(),
        getRadialGradientInitMethodId(),
        sx,
        sy,
        radius,
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