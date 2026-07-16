// Desktop JVM replacements for the pieces of helpers/general.cpp that the
// command queue bindings need. The Android version is entangled with the
// legacy canvas factories and Android system properties, so desktop provides
// just these symbols instead of compiling that file.
#include "helpers/general.hpp"

#include "helpers/rive_log.hpp"

#include <cassert>
#include <cstdlib>

namespace rive_android
{

JavaVM* g_JVM = nullptr;
long g_sdkVersion = 0;

JNIEnv* GetJNIEnv()
{
    JNIEnv* env = nullptr;
    int getEnvStat = g_JVM->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED)
    {
        RiveLogW("RiveN/GetJNIEnv", "GetJNIEnv - Not Attached.");
        if (g_JVM->AttachCurrentThread((void**)&env, nullptr) != 0)
        {
            RiveLogE("RiveN/GetJNIEnv", "Failed to attach current thread.");
        }
    }
    else if (getEnvStat == JNI_EVERSION)
    {
        RiveLogE("RiveN/GetJNIEnv",
                 "GetJNIEnv: unsupported version %d",
                 getEnvStat);
    }
    return env;
}

void DetachThread()
{
    RiveLogD("RiveN/DetachThread", "Detaching thread.");
    if (g_JVM->DetachCurrentThread() != JNI_OK)
    {
        RiveLogE("RiveN/DetachThread", "DetachCurrentThread failed.");
    }
}

// No Android system properties on desktop; leave g_sdkVersion at 0.
void SetSDKVersion() {}

rive::Fit GetFit(uint8_t ordinal) { return static_cast<rive::Fit>(ordinal); }

rive::Alignment GetAlignment(uint8_t ordinal)
{
    switch (ordinal)
    {
        case 0:
            return rive::Alignment::topLeft;
        case 1:
            return rive::Alignment::topCenter;
        case 2:
            return rive::Alignment::topRight;
        case 3:
            return rive::Alignment::centerLeft;
        case 4:
            return rive::Alignment::center;
        case 5:
            return rive::Alignment::centerRight;
        case 6:
            return rive::Alignment::bottomLeft;
        case 7:
            return rive::Alignment::bottomCenter;
        case 8:
            return rive::Alignment::bottomRight;
        default:
            RiveLogE("RiveN/GetAlignment",
                     "Invalid alignment ordinal: %u",
                     ordinal);
            assert(false && "Invalid rive::Alignment ordinal");
            std::abort();
    }
}

#if defined(DEBUG) || defined(LOG)
[[noreturn]] void LogThread()
{
    while (true)
    {
        pause();
    }
}
#endif

} // namespace rive_android

extern "C" JNIEXPORT void JNICALL
Java_app_rive_core_RiveNative_cppSetVulkanLibraryPath(JNIEnv* env,
                                                      jobject,
                                                      jstring jPath)
{
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    setenv("RIVE_VULKAN_LIBRARY_PATH", path, 1);
    env->ReleaseStringUTFChars(jPath, path);
}
