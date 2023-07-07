#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/dimensions_helper.hpp"
#include <jni.h>

#if defined(DEBUG) || defined(LOG)

#include <thread>

#endif

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_Rive_cppCalculateRequiredBounds(JNIEnv* env,
                                                                      jobject thisObj,
                                                                      jobject jfit,
                                                                      jobject jalignment,
                                                                      jobject availableBoundsRectF,
                                                                      jobject artboardBoundsRectF,
                                                                      jobject requiredBoundsRectF)
    {
        auto fit = ::getFit(env, jfit);
        auto alignment = ::getAlignment(env, jalignment);
        auto availableBounds = rectFToAABB(env, availableBoundsRectF);
        auto artboardBounds = rectFToAABB(env, artboardBoundsRectF);

        DimensionsHelper helper;

        auto required = helper.computeDimensions(fit, alignment, availableBounds, artboardBounds);
        aabbToRectF(env, required, requiredBoundsRectF);
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Rive_cppInitialize(JNIEnv* env,
                                                                                jobject thisObj)
    {
#if defined(DEBUG) || defined(LOG)
        // luigi: again ifdef this out for release (or murder completely, but
        // it's nice to catch all fprintf to stderr).
        std::thread t(logThread);
        // detach so it outlives the ref
        t.detach();
#endif
        // pretty much considered the entrypoint.
        env->GetJavaVM(&::globalJavaVM);
    }

#ifdef __cplusplus
}
#endif
