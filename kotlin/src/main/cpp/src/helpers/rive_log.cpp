#include "helpers/rive_log.hpp"
#include "helpers/general.hpp"
#include "helpers/jni_resource.hpp"
#include <cstdio>
#include <cstring>
#include <android/log.h>

namespace rive_android
{

// Cached class and method IDs for RiveLog
static jclass g_riveLogClass = nullptr;
static jmethodID g_riveLogVMethod = nullptr;
static jmethodID g_riveLogDMethod = nullptr;
static jmethodID g_riveLogIMethod = nullptr;
static jmethodID g_riveLogWMethod = nullptr;
static jmethodID g_riveLogEMethod = nullptr;
static std::mutex g_riveLogMutex;
static bool g_riveLogInitialized = false;

static void FallbackToNativeLog(int androidLogLevel,
                                const char* tag,
                                const char* message)
{
    switch (androidLogLevel)
    {
        // LOGV is not defined in general.hpp, so map verbose to debug.
        case ANDROID_LOG_VERBOSE:
        case ANDROID_LOG_DEBUG:
            LOGD("[%s] %s", tag, message);
            break;
        case ANDROID_LOG_INFO:
            LOGI("[%s] %s", tag, message);
            break;
        case ANDROID_LOG_WARN:
            LOGW("[%s] %s", tag, message);
            break;
        case ANDROID_LOG_ERROR:
        default:
            LOGE("[%s] %s", tag, message);
            break;
    }
}

void InitializeRiveLog()
{
    std::lock_guard<std::mutex> lock(g_riveLogMutex);
    // Idempotent initialization
    if (g_riveLogInitialized)
    {
        return;
    }

    JNIEnv* env = GetJNIEnv();
    if (env == nullptr)
    {
        LOGE("RiveLog initialization failed: Unable to get JNIEnv");
        return;
    }

    // Find the RiveLog class
    auto riveLogClass = FindClass(env, "app/rive/RiveLog");
    if (riveLogClass.get() == nullptr)
    {
        LOGE("RiveLog initialization failed: RiveLog class not found");
        return;
    }

    // Cache the class as a global reference
    g_riveLogClass =
        reinterpret_cast<jclass>(env->NewGlobalRef(riveLogClass.get()));
    if (g_riveLogClass == nullptr)
    {
        LOGE("RiveLog initialization failed: "
             "Unable to create global RiveLog class ref");
        return;
    }

    // Get method IDs for the logging methods
    // These call the @JvmStatic methods that take (String, String)
    g_riveLogVMethod =
        env->GetStaticMethodID(g_riveLogClass,
                               "logV",
                               "(Ljava/lang/String;Ljava/lang/String;)V");
    g_riveLogDMethod =
        env->GetStaticMethodID(g_riveLogClass,
                               "logD",
                               "(Ljava/lang/String;Ljava/lang/String;)V");
    g_riveLogIMethod =
        env->GetStaticMethodID(g_riveLogClass,
                               "logI",
                               "(Ljava/lang/String;Ljava/lang/String;)V");
    g_riveLogWMethod =
        env->GetStaticMethodID(g_riveLogClass,
                               "logW",
                               "(Ljava/lang/String;Ljava/lang/String;)V");
    g_riveLogEMethod =
        env->GetStaticMethodID(g_riveLogClass,
                               "logE",
                               "(Ljava/lang/String;Ljava/lang/String;)V");

    // Check if any method ID lookup failed
    if (env->ExceptionCheck())
    {
        LOGE("RiveLog initialization failed: Error getting logging method IDs");
        env->ExceptionDescribe(); // Log the exception details
        env->ExceptionClear();
        // Method IDs will be nullptr, and we'll fallback to android log
    }

    g_riveLogInitialized = true;
}

// Internal helper to format and log a message
static void LogMessage(jmethodID methodID,
                       const char* tag,
                       const char* format,
                       va_list args,
                       int androidLogLevel)
{
    // Format the message using vsnprintf
    char buffer[512];
    va_list argsCopy;
    va_copy(argsCopy, args);
    int result = vsnprintf(buffer, sizeof(buffer), format, argsCopy);
    va_end(argsCopy);

    if (result < 0)
    {
        LOGE("Logging error: vsnprintf failed");
        return;
    }

    // Ensure null termination
    if (static_cast<size_t>(result) >= sizeof(buffer))
    {
        buffer[sizeof(buffer) - 1] = '\0';
    }

    // If the logging isn't ready, fallback to native logging
    if (methodID == nullptr || !g_riveLogInitialized)
    {
        FallbackToNativeLog(androidLogLevel, tag, buffer);
        return;
    }

    JNIEnv* env = nullptr;
    auto getEnvStat = g_JVM->GetEnv((void**)&env, JNI_VERSION_1_6);
    // Happens on native-created threads that were never attached to the
    // JVM (e.g. short-lived cleanup std::thread paths). In that case,
    // skip RiveLog and fall back to native logging.
    if (getEnvStat == JNI_EDETACHED)
    {
        FallbackToNativeLog(androidLogLevel, tag, buffer);
        return;
    }
    else if (getEnvStat != JNI_OK)
    {
        LOGE(
            "Logging error: Unable to get JNIEnv for RiveLog. JNI Error Code: %d",
            getEnvStat);
        FallbackToNativeLog(androidLogLevel, tag, buffer);
        return;
    }

    // Create Kotlin strings for tag and message
    auto jTag = MakeJString(env, tag);
    auto jMessage = MakeJString(env, buffer);

    // Call the static method
    env->CallStaticVoidMethod(g_riveLogClass,
                              methodID,
                              jTag.get(),
                              jMessage.get());

    // Check for exceptions (but don't throw - logging shouldn't crash)
    if (env->ExceptionCheck())
    {
        LOGE("Logging error: Exception occurred in RiveLog method");
        env->ExceptionDescribe(); // Log the exception details
        env->ExceptionClear();
        // Fall through to Android logging fallback
        FallbackToNativeLog(androidLogLevel, tag, buffer);
    }
}

void RiveLogV(const char* tag, const char* format, ...)
{
    va_list args;
    va_start(args, format);
    LogMessage(g_riveLogVMethod, tag, format, args, ANDROID_LOG_VERBOSE);
    va_end(args);
}

void RiveLogD(const char* tag, const char* format, ...)
{
    va_list args;
    va_start(args, format);
    LogMessage(g_riveLogDMethod, tag, format, args, ANDROID_LOG_DEBUG);
    va_end(args);
}

void RiveLogI(const char* tag, const char* format, ...)
{
    va_list args;
    va_start(args, format);
    LogMessage(g_riveLogIMethod, tag, format, args, ANDROID_LOG_INFO);
    va_end(args);
}

void RiveLogW(const char* tag, const char* format, ...)
{
    va_list args;
    va_start(args, format);
    LogMessage(g_riveLogWMethod, tag, format, args, ANDROID_LOG_WARN);
    va_end(args);
}

void RiveLogE(const char* tag, const char* format, ...)
{
    va_list args;
    va_start(args, format);
    LogMessage(g_riveLogEMethod, tag, format, args, ANDROID_LOG_ERROR);
    va_end(args);
}

} // namespace rive_android
