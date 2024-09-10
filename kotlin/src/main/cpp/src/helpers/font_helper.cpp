#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "helpers/font_helper.hpp"
#include "helpers/jni_resource.hpp"

namespace rive_android
{

std::vector<rive::rcp<rive::Font>> FontHelper::fallbackFonts;

bool FontHelper::registerFallbackFont(jbyteArray byteArray)
{
    std::vector<uint8_t> bytes = ByteArrayToUint8Vec(GetJNIEnv(), byteArray);

    rive::rcp<rive::Font> fallback = HBFont::Decode(bytes);
    if (!fallback)
    {
        LOGE("registerFallbackFont - failed to decode byte fonts");
        return false;
    }

    fallbackFonts.push_back(fallback);
    return true;
}

std::vector<uint8_t> FontHelper::getSystemFontBytes()
{
    JNIEnv* env = GetJNIEnv();
    // Find the FontHelper class
    JniResource<jclass> fontHelperClass =
        FindClass<jclass>(env, "app/rive/runtime/kotlin/fonts/FontHelper");
    if (!fontHelperClass)
    {
        LOGE("FontHelper class not found");
        return {};
    }

    // Get the Companion field ID
    jfieldID fontCompanionField =
        env->GetStaticFieldID(fontHelperClass.get(),
                              "Companion",
                              "Lapp/rive/runtime/kotlin/fonts/FontHelper$Companion;");
    if (!fontCompanionField)
    {
        LOGE("FontHelper Companion field not found");
        return {};
    }

    // Get the Companion object
    JniResource<jobject> companionObject =
        GetStaticObjectField<jobject>(env, fontHelperClass, fontCompanionField);
    if (!companionObject)
    {
        LOGE("Could not get FontHelper Companion object");
        return {};
    }

    // Find the Companion class
    JniResource<jclass> fontHelperCompanionClass =
        FindClass<jclass>(env, "app/rive/runtime/kotlin/fonts/FontHelper$Companion");
    if (!fontHelperCompanionClass)
    {
        LOGE("FontHelper Companion class not found");
        return {};
    }

    // Get the getFallbackFontBytes method ID
    jmethodID getFontBytesMethodId =
        env->GetMethodID(fontHelperCompanionClass.get(),
                         "getFallbackFontBytes",
                         "(Lapp/rive/runtime/kotlin/fonts/Fonts$FontOpts;)[B");
    if (!getFontBytesMethodId)
    {
        LOGE("FontHelper did not find getFallbackFontBytes() method");
        return {};
    }

    // Call the method
    JniResource<jbyteArray> fontBytes = JniResource<jbyteArray>(
        static_cast<jbyteArray>(
            env->CallObjectMethod(companionObject, getFontBytesMethodId, nullptr)),
        env);
    if (!fontBytes)
    {
        LOGE("FontHelper couldn't load fallback font from the system");
        return {};
    }

    return ByteArrayToUint8Vec(env, fontBytes);
}

rive::rcp<rive::Font> FontHelper::findFontFallback(rive::Span<const rive::Unichar> missing)
{
    for (const rive::rcp<rive::Font>& font : fallbackFonts)
    {
        bool found = font->hasGlyph(missing);
        if (font->hasGlyph(missing))
        {
            return font;
        }
    }

    // Nothing in the registered fallbacks? Grab one from the system
    std::vector<uint8_t> fontBytes = FontHelper::getSystemFontBytes();
    if (fontBytes.empty())
    {
        LOGW("findFontFallback - No fonts found on the system");
        return nullptr;
    }

    rive::rcp<rive::Font> systemFont = HBFont::Decode(fontBytes);
    if (!systemFont)
    {
        LOGE("findFontFallback - failed to decode font bytes");
        return nullptr;
    }

    return systemFont->hasGlyph(missing) ? systemFont : nullptr;
}

} // namespace rive_android