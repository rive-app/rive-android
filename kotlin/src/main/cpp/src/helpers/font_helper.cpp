#include "helpers/general.hpp"
#include "helpers/font_helper.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/jni_resource.hpp"

namespace rive_android
{

/* static */ std::vector<rive::rcp<rive::Font>> FontHelper::s_fallbackFonts;
/* static */ std::unordered_map<const rive::Font*, rive::rcp<rive::Font>>
    FontHelper::s_fallbackFontsCache;

/* static */ bool FontHelper::RegisterFallbackFont(jbyteArray byteArray)
{
    std::vector<uint8_t> bytes = ByteArrayToUint8Vec(GetJNIEnv(), byteArray);

    rive::rcp<rive::Font> fallback = HBFont::Decode(bytes);
    if (!fallback)
    {
        LOGE("RegisterFallbackFont - failed to decode byte fonts");
        return false;
    }

    s_fallbackFonts.push_back(fallback);
    return true;
}

/* static */ std::vector<uint8_t> FontHelper::GetSystemFontBytes()
{
    JNIEnv* env = GetJNIEnv();
    // Find the FontHelper class
    JniResource<jclass> fontHelperClass =
        FindClass(env, "app/rive/runtime/kotlin/fonts/FontHelper");
    if (!fontHelperClass.get())
    {
        LOGE("FontHelper class not found");
        return {};
    }

    // Get the Companion field ID
    jfieldID fontCompanionField = env->GetStaticFieldID(
        fontHelperClass.get(),
        "Companion",
        "Lapp/rive/runtime/kotlin/fonts/FontHelper$Companion;");
    if (!fontCompanionField)
    {
        LOGE("FontHelper Companion field not found");
        return {};
    }

    // Get the Companion object
    JniResource<jobject> companionObject =
        GetStaticObjectField(env, fontHelperClass.get(), fontCompanionField);
    if (!companionObject.get())
    {
        LOGE("Could not get FontHelper Companion object");
        return {};
    }

    // Find the Companion class
    JniResource<jclass> fontHelperCompanionClass =
        FindClass(env, "app/rive/runtime/kotlin/fonts/FontHelper$Companion");
    if (!fontHelperCompanionClass.get())
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
            JNIExceptionHandler::CallObjectMethod(env,
                                                  companionObject.get(),
                                                  getFontBytesMethodId,
                                                  nullptr)),
        env);
    if (!fontBytes.get())
    {
        LOGE("FontHelper couldn't load fallback font from the system");
        return {};
    }

    return ByteArrayToUint8Vec(env, fontBytes.get());
}

/* static */ std::vector<std::vector<uint8_t>> FontHelper::pick_fonts(
    uint16_t weight)
{
    JNIEnv* env = GetJNIEnv();
    JniResource<jclass> pickerClass =
        FindClass(env, "app/rive/runtime/kotlin/fonts/FontFallbackStrategy");

    // Get the Companion field ID
    jfieldID fontCompanionField = env->GetStaticFieldID(
        pickerClass.get(),
        "Companion",
        "Lapp/rive/runtime/kotlin/fonts/FontFallbackStrategy$Companion;");

    // Get the Companion object
    JniResource<jobject> companionObject =
        GetStaticObjectField(env, pickerClass.get(), fontCompanionField);

    // Find the Companion class
    JniResource<jclass> pickerCompanionClass = FindClass(
        env,
        "app/rive/runtime/kotlin/fonts/FontFallbackStrategy$Companion");

    jmethodID pickFontMid = env->GetMethodID(pickerCompanionClass.get(),
                                             "pickFont",
                                             "(I)Ljava/util/List;");

    // Call the static pickFont method
    JniResource<jobject> fontListObj =
        GetObjectFromMethod(env, companionObject.get(), pickFontMid, weight);

    // Convert Java List<ByteArray> to std::vector<std::vector<uint8_t>>
    std::vector<std::vector<uint8_t>> result;

    // Get the List class and methods
    JniResource<jclass> listClass = GetObjectClass(env, fontListObj.get());
    jmethodID listSizeMethod = env->GetMethodID(listClass.get(), "size", "()I");
    jmethodID listGetMethod =
        env->GetMethodID(listClass.get(), "get", "(I)Ljava/lang/Object;");

    jint listSize = JNIExceptionHandler::CallIntMethod(env,
                                                       fontListObj.get(),
                                                       listSizeMethod);
    for (jint i = 0; i < listSize; ++i)
    {
        JniResource<jobject> byteArrayObj =
            GetObjectFromMethod(env, fontListObj.get(), listGetMethod, i);

        // Convert ByteArray to std::vector<uint8_t>
        jbyteArray byteArray = reinterpret_cast<jbyteArray>(byteArrayObj.get());
        jsize arrayLength = env->GetArrayLength(byteArray);
        std::vector<uint8_t> byteVector(arrayLength);
        env->GetByteArrayRegion(byteArray,
                                0,
                                arrayLength,
                                reinterpret_cast<jbyte*>(byteVector.data()));

        result.push_back(std::move(byteVector));
    }

    return result;
}

/**
 * Finds a fallback font that can be used for a missing character.
 *
 * This function attempts to find a font that can be used as a fallback when the
 * provided `riveFont` does not contain the requested character (`missing`). The
 * steps for determining the appropriate fallback are as follows:
 *
 * 1. If the font has already been processed and cached, it returns the cached
 * fallback font.
 * 2. The function then attempts to find fallback fonts based on the desired
 * weight of the provided font (`riveFont`). This'll check whether a
 * `FontFallbackStrategy` has been set in Kotlin and, if so, it'll use that to
 * find a suitable match.
 * 3. If `FontFallbackStrategy` is not defined or didn't yield a suitable font,
 * it will iterate over a list of registered fallback fonts: if this list
 * contains a match, it is cached and returned.
 * 4. Finally, if no registered fallback fonts contain the missing character,
 * the function attempts to load the default system fallback font.
 *
 */
/* static */ rive::rcp<rive::Font> FontHelper::FindFontFallback(
    const rive::Unichar missing,
    const uint32_t fallbackIndex,
    const rive::Font* riveFont)
{
    if (fallbackIndex > 0)
    {
        // Cannot attempt more than once on Android, otherwise it will keep
        // trying and risk a stack overflow.
        return nullptr;
    }

    if (!riveFont)
    {
        LOGE("No font provided for missing characters");
        return nullptr;
    }

    uint16_t desiredWeight = riveFont->getWeight();
    if (auto search = s_fallbackFontsCache.find(riveFont);
        search != s_fallbackFontsCache.end())
    {
        return search->second;
    }

    auto pickedFonts = pick_fonts(desiredWeight);
    for (const auto& fontBytes : pickedFonts)
    {
        rive::rcp<rive::Font> candidate = HBFont::Decode(fontBytes);
        if (candidate->hasGlyph(missing))
        {
            s_fallbackFontsCache[riveFont] = candidate;
            return candidate;
        }
    }

    // Use the old path - just try to find a match for this glyph.
    for (const rive::rcp<rive::Font>& fFont : s_fallbackFonts)
    {
        if (fFont->hasGlyph(missing))
        {
            s_fallbackFontsCache[riveFont] = fFont;
            return fFont;
        }
    }

    // Nothing in the registered fallbacks? Grab one from the system
    std::vector<uint8_t> fontBytes = FontHelper::GetSystemFontBytes();
    if (fontBytes.empty())
    {
        LOGW("FindFontFallback - No system font found");
        return nullptr;
    }

    rive::rcp<rive::Font> systemFont = HBFont::Decode(fontBytes);
    if (!systemFont)
    {
        LOGE("FindFontFallback - failed to decode system font bytes");
        return nullptr;
    }

    if (!systemFont->hasGlyph(missing))
    {
        LOGE("FindFontFallback - no fallback found");
        return nullptr;
    }

    LOGD("FindFontFallback - found a system fallback");
    s_fallbackFontsCache[riveFont] = systemFont;
    return systemFont;
}

} // namespace rive_android