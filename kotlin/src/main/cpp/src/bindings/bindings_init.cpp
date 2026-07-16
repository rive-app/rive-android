#include <jni.h>

#ifdef __ANDROID__
#include "helpers/font_helper.hpp"
#endif
#include "helpers/general.hpp"
#include "helpers/jni_resource.hpp"
#include "helpers/rive_log.hpp"

#if defined(DEBUG) || defined(LOG)

#include <thread>

#endif

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    jint JNI_OnLoad(JavaVM* jvm, void*)
    {
        // Assign the global JVM
        g_JVM = jvm;
        // Standard JNI version to return on Android
        return JNI_VERSION_1_6;
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_RiveNative_cppInitialize(JNIEnv* env, jobject jObj)
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

        // Use the calling RiveNative jobject as the "anchor" to initialize the
        // global class loader.
        RiveLogD(TAG, "Initializing global class loader");
        InitJNIClassLoader(env, jObj);

#ifdef __ANDROID__
        // System font fallback enumeration is Android-only; other platforms
        // rely on fonts embedded in the .riv or registered explicitly.
        RiveLogD(TAG, "Initializing fallback font global callback");
        rive::Font::gFallbackProc = FontHelper::FindFontFallback;
#endif
    }

#ifdef __cplusplus
}
#endif
