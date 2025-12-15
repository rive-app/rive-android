#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "helpers/font_helper.hpp"
#include "helpers/rive_log.hpp"
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
    Java_app_rive_runtime_kotlin_core_Rive_cppCalculateRequiredBounds(
        JNIEnv* env,
        jobject,
        jobject jFit,
        jobject jAlignment,
        jobject availableBoundsRectF,
        jobject artboardBoundsRectF,
        jobject requiredBoundsRectF,
        jfloat scaleFactor)
    {
        auto fit = ::GetFit(env, jFit);
        auto alignment = ::GetAlignment(env, jAlignment);
        auto availableBounds = RectFToAABB(env, availableBoundsRectF);
        auto artboardBounds = RectFToAABB(env, artboardBoundsRectF);

        DimensionsHelper helper;

        auto required = helper.computeDimensions(fit,
                                                 alignment,
                                                 availableBounds,
                                                 artboardBounds,
                                                 scaleFactor);
        AABBToRectF(env, required, requiredBoundsRectF);
    }

    jint JNI_OnLoad(JavaVM* jvm, void*)
    {
        // Assign the global JVM
        g_JVM = jvm;
        // Standard JNI version to return on Android
        return JNI_VERSION_1_6;
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_Rive_cppInitialize(JNIEnv*, jobject)
    {
        const auto TAG = "RiveN/Init";
#if defined(DEBUG) || defined(LOG)
        // luigi: again ifdef this out for release (or murder completely, but
        // it's nice to catch all fprintf to stderr).
        std::thread t(LogThread);
        // detach so it outlives the ref
        t.detach();
#endif
        // pretty much considered the entrypoint.
        SetSDKVersion();
        // Initialize RiveLog helper for logging from C++
        InitializeRiveLog();

        RiveLogD(TAG, "Initializing fallback font global callback");
        rive::Font::gFallbackProc = FontHelper::FindFontFallback;
    }

#ifdef __cplusplus
}
#endif
