#include "helpers/general.hpp"

#include "helpers/android_factories.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/rive_log.hpp"
#include "helpers/worker_ref.hpp"
#include "jni_refs.hpp"
#include "rive/file.hpp"

#if defined(DEBUG) || defined(LOG)
#include <EGL/egl.h>
#include <cerrno>
#include <cstdio>
#include <unistd.h>
#endif

namespace rive_android
{
/**
 * Global factories that are used to instantiate render objects (paths, buffers,
 * textures, etc.)
 */
static AndroidRiveRenderFactory g_RiveFactory;
static AndroidCanvasFactory g_CanvasFactory;

JavaVM* g_JVM = nullptr;
long g_sdkVersion;

JNIEnv* GetJNIEnv()
{
    // double check it's all ok
    JNIEnv* g_env = nullptr;
    int getEnvStat = g_JVM->GetEnv((void**)&g_env, JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED)
    {
        RiveLogW("RiveN/GetJNIEnv", "GetJNIEnv - Not Attached.");
        if (g_JVM->AttachCurrentThread((JNIEnv**)&g_env, nullptr) != 0)
        {
            RiveLogE("RiveN/GetJNIEnv", "Failed to attach current thread.");
        }
    }
    else if (getEnvStat == JNI_OK)
    {
        //
    }
    else if (getEnvStat == JNI_EVERSION)
    {
        RiveLogE("RiveN/GetJNIEnv",
                 "GetJNIEnv: unsupported version %d",
                 getEnvStat);
    }
    return g_env;
}

void DetachThread()
{
    RiveLogD("RiveLN/DetachThread", "Detaching thread.");
    if (g_JVM->DetachCurrentThread() != JNI_OK)
    {
        RiveLogE("RiveN/GetJNIEnv", "DetachCurrentThread failed.");
    }
}

void LogReferenceTables()
{
    jclass vm_class = GetJNIEnv()->FindClass("dalvik/system/VMDebug");
    jmethodID dump_mid =
        GetJNIEnv()->GetStaticMethodID(vm_class, "dumpReferenceTables", "()V");
    GetJNIEnv()->CallStaticVoidMethod(vm_class, dump_mid);
}

void SetSDKVersion()
{
    char sdk_ver_str[255];
    __system_property_get("ro.build.version.sdk", sdk_ver_str);
    g_sdkVersion = strtol(sdk_ver_str, nullptr, 10);
}

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


rive::Factory* GetFactory(RendererType rendererType)
{
    if (rendererType == RendererType::Rive &&
        RefWorker::RiveWorker() != nullptr)
    {
        return static_cast<rive::Factory*>(&g_RiveFactory);
    }
    // Current fallback is Canvas.
    return static_cast<rive::Factory*>(&g_CanvasFactory);
}


#if defined(DEBUG) || defined(LOG)
[[noreturn]] void LogThread()
{
    int pipes[2];
    pipe(pipes);
    dup2(pipes[1], STDERR_FILENO);
    FILE* inputFile = fdopen(pipes[0], "r");
    char readBuffer[256];
    while (true)
    {
        fgets(readBuffer, sizeof(readBuffer), inputFile);
        __android_log_write(2, "rive_stderr", readBuffer);
    }
}

void _check_egl_error(const char* file, int line)
{
    static constexpr auto* TAG = "RiveLN/check_egl_error";

    EGLenum err(eglGetError());

    while (true)
    {
        std::string error;

        switch (err)
        {
            case EGL_SUCCESS:
                return;
            case EGL_NOT_INITIALIZED:
                error = "EGL_NOT_INITIALIZED";
                break;
            case EGL_BAD_ACCESS:
                error = "EGL_BAD_ACCESS";
                break;
            case EGL_BAD_ALLOC:
                error = "EGL_BAD_ALLOC";
                break;
            case EGL_BAD_ATTRIBUTE:
                error = "EGL_BAD_ATTRIBUTE";
                break;
            case EGL_BAD_CONTEXT:
                error = "EGL_BAD_CONTEXT";
                break;
            case EGL_BAD_CONFIG:
                error = "EGL_BAD_CONFIG";
                break;
            case EGL_BAD_CURRENT_SURFACE:
                error = "EGL_BAD_CURRENT_SURFACE";
                break;
            case EGL_BAD_DISPLAY:
                error = "EGL_BAD_DISPLAY";
                break;
            case EGL_BAD_SURFACE:
                error = "EGL_BAD_SURFACE";
                break;
            case EGL_BAD_MATCH:
                error = "EGL_BAD_MATCH";
                break;
            case EGL_BAD_PARAMETER:
                error = "EGL_BAD_PARAMETER";
                break;
            case EGL_BAD_NATIVE_PIXMAP:
                error = "EGL_BAD_NATIVE_PIXMAP";
                break;
            case EGL_BAD_NATIVE_WINDOW:
                error = "EGL_BAD_NATIVE_WINDOW";
                break;
            case EGL_CONTEXT_LOST:
                error = "EGL_CONTEXT_LOST";
                break;
            default:
                RiveLogE(TAG, "(%d) %s - %s:%d", err, "Unknown", file, line);
                return;
        }
        RiveLogE(TAG, "(%d) %s - %s:%d", err, error.c_str(), file, line);
        err = eglGetError();
    }
}
#endif
} // namespace rive_android
