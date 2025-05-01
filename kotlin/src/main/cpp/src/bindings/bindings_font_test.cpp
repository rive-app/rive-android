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
        FontHelper::resetCache();
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

    JNIEXPORT jint JNICALL
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

        // Try to find a font with the missing code point
        int fallbackIndex = 0;
        rive::rcp<rive::Font> fontWithGlyph =
            FontHelper::FindFontFallback(missingCodePoint,
                                         fallbackIndex,
                                         aFont.get());

        // Search through fallbacks until we find one with the glyph or run out
        // of options
        while (fontWithGlyph != nullptr &&
               !fontWithGlyph->hasGlyph(missingCodePoint))
        {
            fallbackIndex++;
            fontWithGlyph = FontHelper::FindFontFallback(missingCodePoint,
                                                         fallbackIndex,
                                                         aFont.get());
        }

        LOGI("Font fallback search result: %s",
             fontWithGlyph != nullptr ? "FOUND" : "NOT FOUND");

        aFont->ref(); // Kept alive for testing.
        // Use this convention for testing:
        // return -1 if we didn't find a fallback
        // return fallbackIndex to signal which index found the match
        return fontWithGlyph != nullptr ? fallbackIndex : -1;
    }

#ifdef __cplusplus
}
#endif

#endif // DEBUG
