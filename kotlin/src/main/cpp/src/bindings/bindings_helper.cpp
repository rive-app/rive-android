#include "models/dimensions_helper.hpp"
#include "helpers/general.hpp"
#include "jni_refs.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    JNIEXPORT jobject JNICALL
    Java_app_rive_runtime_kotlin_core_Helpers_cppConvertToArtboardSpace(
        JNIEnv* env,
        jobject,
        jobject touchSpaceRectF,
        jobject touchSpacePointF,
        jobject jFit,
        jobject jAlignment,
        jobject artboardSpaceRectF,
        jfloat scaleFactor)
    {
        auto fit = ::GetFit(env, jFit);
        auto alignment = ::GetAlignment(env, jAlignment);
        auto artboardSpaceBounds = RectFToAABB(env, artboardSpaceRectF);
        auto touchSpaceBounds = RectFToAABB(env, touchSpaceRectF);
        jlong touchX =
            (jlong)env->GetFloatField(touchSpacePointF, GetXFieldId());
        jlong touchY =
            (jlong)env->GetFloatField(touchSpacePointF, GetYFieldId());

        rive::Mat2D forward = rive::computeAlignment(fit,
                                                     alignment,
                                                     touchSpaceBounds,
                                                     artboardSpaceBounds,
                                                     scaleFactor);
        rive::Mat2D inverse = forward.invertOrIdentity();

        auto touchLocation =
            rive::Vec2D(static_cast<float>(touchX), static_cast<float>(touchY));
        rive::Vec2D convertedLocation = inverse * touchLocation;

        return env->NewObject(GetPointerFClass(),
                              GetPointFInitMethod(),
                              convertedLocation.x,
                              convertedLocation.y);
    }

#ifdef __cplusplus
}
#endif
