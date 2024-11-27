/**
 * Testing functions
 */
#ifdef DEBUG

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

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppCleanupFallbacks(
        JNIEnv*,
        jobject)
    {
        FontHelper::s_fallbackFonts.clear();
    }

    JNIEXPORT jbyteArray JNICALL
    Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppGetSystemFontBytes(
        JNIEnv* env,
        jobject)
    {
        std::vector<uint8_t> bytes = FontHelper::GetSystemFontBytes();
        auto len = SizeTTOInt(bytes.size());
        if (len == 0)
        {
            LOGE("cppGetSystemFontBytes - GetSystemFontBytes() returned no "
                 "data");
            return {};
        }
        jbyteArray byteArray = env->NewByteArray(len);
        env->SetByteArrayRegion(byteArray,
                                0,
                                len,
                                reinterpret_cast<const jbyte*>(bytes.data()));

        return byteArray;
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppFindFontFallback(
        JNIEnv*,
        jobject,
        jint missingCodePoint,
        jbyteArray fontBytes)
    {
        // Optionally pass in a font as reference.
        std::vector<uint8_t> bytes =
            ByteArrayToUint8Vec(GetJNIEnv(), fontBytes);
        rive::rcp<rive::Font> aFont = HBFont::Decode(bytes);

        auto font =
            FontHelper::FindFontFallback(missingCodePoint, 0, aFont.get());

        aFont->ref(); // Kept alive for testing.
        return font != nullptr;
    }

#ifdef __cplusplus
}
#endif

#endif // DEBUG
