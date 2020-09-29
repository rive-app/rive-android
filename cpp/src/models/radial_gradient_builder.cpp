#include "helpers/general.hpp"
#include "models/radial_gradient_builder.hpp"

// From rive-cpp
#include "math/vec2d.hpp"
//

using namespace rive_android;

void JNIRadialGradientBuilder::apply(jobject paint)
{
    int numStops = stops.size();

    jintArray jcolors = globalJNIEnv->NewIntArray(numStops);
    jfloatArray jstops = globalJNIEnv->NewFloatArray(numStops);
    globalJNIEnv->SetIntArrayRegion(jcolors, 0, numStops, colors.data());
    globalJNIEnv->SetFloatArrayRegion(jstops, 0, numStops, stops.data());

    float radius = rive::Vec2D::distance(rive::Vec2D(sx, sy), rive::Vec2D(ex, ey));

    jobject shaderObject = globalJNIEnv->NewObject(
        radialGradientClass,
        radialGradientInitMethodId,
        sx,
        sy,
        radius,
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