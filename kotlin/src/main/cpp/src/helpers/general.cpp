#include "helpers/general.hpp"

#include "jni_refs.hpp"
#include "helpers/android_factories.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/rive_log.hpp"
#include "helpers/worker_ref.hpp"
#include "rive/file.hpp"

#if defined(DEBUG) || defined(LOG)
#include <cerrno>
#include <cstdio>
#include <unistd.h>
#include <EGL/egl.h>
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

rive::Fit GetFit(JNIEnv* env, jobject jFit)
{
    auto fitValue = (jstring)JNIExceptionHandler::CallObjectMethod(
        env,
        jFit,
        rive_android::GetFitNameMethodId());
    const char* fitValueNative = env->GetStringUTFChars(fitValue, nullptr);

    rive::Fit fit = rive::Fit::none;
    if (strcmp(fitValueNative, "FILL") == 0)
    {
        fit = rive::Fit::fill;
    }
    else if (strcmp(fitValueNative, "CONTAIN") == 0)
    {
        fit = rive::Fit::contain;
    }
    else if (strcmp(fitValueNative, "COVER") == 0)
    {
        fit = rive::Fit::cover;
    }
    else if (strcmp(fitValueNative, "FIT_WIDTH") == 0)
    {
        fit = rive::Fit::fitWidth;
    }
    else if (strcmp(fitValueNative, "FIT_HEIGHT") == 0)
    {
        fit = rive::Fit::fitHeight;
    }
    else if (strcmp(fitValueNative, "NONE") == 0)
    {
        fit = rive::Fit::none;
    }
    else if (strcmp(fitValueNative, "SCALE_DOWN") == 0)
    {
        fit = rive::Fit::scaleDown;
    }
    else if (strcmp(fitValueNative, "LAYOUT") == 0)
    {
        fit = rive::Fit::layout;
    }
    env->ReleaseStringUTFChars(fitValue, fitValueNative);
    env->DeleteLocalRef(fitValue);
    return fit;
}

rive::Alignment GetAlignment(JNIEnv* env, jobject jAlignment)
{
    auto alignmentValue = (jstring)JNIExceptionHandler::CallObjectMethod(
        env,
        jAlignment,
        rive_android::GetAlignmentNameMethodId());
    const char* alignmentValueNative =
        env->GetStringUTFChars(alignmentValue, nullptr);

    rive::Alignment alignment = rive::Alignment::center;
    if (strcmp(alignmentValueNative, "TOP_LEFT") == 0)
    {
        alignment = rive::Alignment::topLeft;
    }
    else if (strcmp(alignmentValueNative, "TOP_CENTER") == 0)
    {
        alignment = rive::Alignment::topCenter;
    }
    else if (strcmp(alignmentValueNative, "TOP_RIGHT") == 0)
    {
        alignment = rive::Alignment::topRight;
    }
    else if (strcmp(alignmentValueNative, "CENTER_LEFT") == 0)
    {
        alignment = rive::Alignment::centerLeft;
    }
    else if (strcmp(alignmentValueNative, "CENTER") == 0)
    {
        alignment = rive::Alignment::center;
    }
    else if (strcmp(alignmentValueNative, "CENTER_RIGHT") == 0)
    {
        alignment = rive::Alignment::centerRight;
    }
    else if (strcmp(alignmentValueNative, "BOTTOM_LEFT") == 0)
    {
        alignment = rive::Alignment::bottomLeft;
    }
    else if (strcmp(alignmentValueNative, "BOTTOM_CENTER") == 0)
    {
        alignment = rive::Alignment::bottomCenter;
    }
    else if (strcmp(alignmentValueNative, "BOTTOM_RIGHT") == 0)
    {
        alignment = rive::Alignment::bottomRight;
    }
    env->ReleaseStringUTFChars(alignmentValue, alignmentValueNative);
    env->DeleteLocalRef(alignmentValue);
    return alignment;
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

jlong Import(uint8_t* bytes,
             jint length,
             RendererType rendererType,
             rive::FileAssetLoader* assetLoader)
{
    rive::ImportResult result;
    auto* fileFactory = GetFactory(rendererType);
    auto* file = rive::File::import(rive::Span<const uint8_t>(bytes, length),
                                    fileFactory,
                                    &result,
                                    assetLoader)
                     // Release the RCP to a raw pointer.
                     // We will un-ref when deleting the file.
                     .release();
    if (result == rive::ImportResult::success)
    {
        return reinterpret_cast<jlong>(file);
    }
    else if (result == rive::ImportResult::unsupportedVersion)
    {
        return ThrowUnsupportedRuntimeVersionException(
            "Unsupported Rive File Version.");
    }
    else if (result == rive::ImportResult::malformed)
    {
        return ThrowMalformedFileException("Malformed Rive File.");
    }
    else
    {
        return ThrowRiveException("Unknown error loading file.");
    }
}

std::string JStringToString(JNIEnv* env, jstring jStr)
{
    if (jStr == nullptr)
    {
        return {};
    }
    auto* cStr = env->GetStringUTFChars(jStr, nullptr);
    auto str = std::string(cStr);
    env->ReleaseStringUTFChars(jStr, cStr);
    return str;
}

int SizeTTOInt(size_t sizeT)
{
    return sizeT > INT_MAX ? INT_MAX : static_cast<int>(sizeT);
}

size_t JIntToSizeT(jint jintValue)
{
    if (jintValue < 0)
    {
        LOGW("JIntToSizeT() - value is a negative number %d", jintValue);
        return 0;
    }
    return jintValue > SIZE_T_MAX ? SIZE_T_MAX : static_cast<size_t>(jintValue);
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
                LOGE("(%d) %s - %s:%d", err, "Unknown", file, line);
                return;
        }
        LOGE("(%d) %s - %s:%d", err, error.c_str(), file, line);
        err = eglGetError();
    }
}
#endif
} // namespace rive_android
