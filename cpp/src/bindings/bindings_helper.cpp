#include "models/jni_renderer.hpp"
#include "models/dimensions_helper.hpp"
#include "helpers/general.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Rive_cppCalculateRequiredBounds(
        JNIEnv *env,
        jobject thisObj,
        jobject jfit,
        jobject jalignment,
        jlong availableBoundsRef,
        jlong artboardBoundsRef,
        jlong requiredBoundsRef)
    {
        auto fit = ::getFit(env, jfit);
        auto alignment = ::getAlignment(env, jalignment);
        rive::AABB *availableBounds = (rive::AABB *)availableBoundsRef;
        rive::AABB *artboardBounds = (rive::AABB *)artboardBoundsRef;
        rive::AABB *requiredBounds = (rive::AABB *)requiredBoundsRef;

        ::DimensionsHelper helper;

        helper.computeDimensions(
            fit, alignment, *availableBounds, *artboardBounds, *requiredBounds);
    }

#ifdef __cplusplus
}
#endif
