#include <jni.h>

#include "helpers/font_helper.hpp"
#include "helpers/general.hpp"
#include "rive/text/utf.hpp"

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    JNIEXPORT jbyteArray JNICALL
    Java_app_rive_runtime_kotlin_fonts_NativeFontHelper_cppGetSystemFontBytes(
        JNIEnv* env,
        jobject)
    {
        std::vector<uint8_t> bytes = FontHelper::getSystemFontBytes();
        auto len = SizeTTOInt(bytes.size());
        if (len == 0)
        {
            LOGE("cppGetSystemFontBytes - getSystemFontBytes() returned no "
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
    Java_app_rive_runtime_kotlin_fonts_NativeFontHelper_cppRegisterFallbackFont(
        JNIEnv* env,
        jobject,
        jbyteArray fontByteArray)
    {
        return FontHelper::registerFallbackFont(fontByteArray);
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_fonts_NativeFontHelper_cppHasGlyph(
        JNIEnv* env,
        jobject,
        jstring ktString)
    {
        const char* utf8Chars = env->GetStringUTFChars(ktString, NULL);
        if (!utf8Chars)
        {
            return false;
        }

        std::vector<rive::Unichar> unichars;
        const uint8_t* ptr = (const uint8_t*)utf8Chars;
        while (*ptr)
        {
            unichars.push_back(rive::UTF::NextUTF8(&ptr));
        }
        if (unichars.empty())
        {
            return false;
        }
        rive::rcp<rive::Font> fallback =
            FontHelper::findFontFallback(unichars[0], 0);

        return fallback != nullptr;
    }

#ifdef __cplusplus
}
#endif
