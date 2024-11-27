#include <jni.h>

#include "helpers/font_helper.hpp"
#include "helpers/general.hpp"
#include "rive/text/utf.hpp"
#include "helpers/jni_resource.hpp"

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_fonts_NativeFontHelper_cppRegisterFallbackFont(
        JNIEnv* env,
        jobject,
        jbyteArray fontByteArray)
    {
        return FontHelper::RegisterFallbackFont(fontByteArray);
    }
#ifdef __cplusplus
}
#endif
