#include "helpers/font_helper.hpp"

#include "helpers/general.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/jni_resource.hpp"
#include "helpers/rive_log.hpp"

namespace rive_android
{
/* static */ std::vector<rive::rcp<rive::Font>> FontHelper::s_fallbackFonts;
/* static */ std::unordered_map<uint16_t, std::vector<rive::rcp<rive::Font>>>
    FontHelper::s_pickFontCache;
/* static */ std::mutex FontHelper::s_fallbackFontsMutex;

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
        RiveLogE("RiveN/FontHelper", "FontHelper class not found");
        return {};
    }

    // Get the Companion field ID
    jfieldID fontCompanionField = env->GetStaticFieldID(
        fontHelperClass.get(),
        "Companion",
        "Lapp/rive/runtime/kotlin/fonts/FontHelper$Companion;");
    if (!fontCompanionField)
    {
        RiveLogE("RiveN/FontHelper", "FontHelper Companion field not found");
        return {};
    }

    // Get the Companion object
    JniResource<jobject> companionObject =
        GetStaticObjectField(env, fontHelperClass.get(), fontCompanionField);
    if (!companionObject.get())
    {
        RiveLogE("RiveN/FontHelper",
                 "Could not get FontHelper Companion object");
        return {};
    }

    // Find the Companion class
    JniResource<jclass> fontHelperCompanionClass =
        FindClass(env, "app/rive/runtime/kotlin/fonts/FontHelper$Companion");
    if (!fontHelperCompanionClass.get())
    {
        RiveLogE("RiveN/FontHelper", "FontHelper Companion class not found");
        return {};
    }

    // Get the getFallbackFontBytes method ID
    jmethodID getFontBytesMethodId =
        env->GetMethodID(fontHelperCompanionClass.get(),
                         "getFallbackFontBytes",
                         "(Lapp/rive/runtime/kotlin/fonts/Fonts$FontOpts;)[B");
    if (!getFontBytesMethodId)
    {
        RiveLogE("RiveN/FontHelper",
                 "FontHelper did not find getFallbackFontBytes() method");
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
        RiveLogE("RiveN/FontHelper",
                 "FontHelper couldn't load fallback font from the system");
        return {};
    }

    return ByteArrayToUint8Vec(env, fontBytes.get());
}

/* static */ const std::vector<rive::rcp<rive::Font>>& FontHelper::PickFonts(
    uint16_t weight)
{
    // Stable return value (referenced) when aborting due to errors.
    // Always empty.
    static const std::vector<rive::rcp<rive::Font>> ERROR_VEC;

    // Check if weight exists in cache first
    auto cacheIt = s_pickFontCache.find(weight);
    if (cacheIt != s_pickFontCache.end())
    {
        return cacheIt->second;
    }

    // Original JNI implementation for cache misses
    JNIEnv* env = GetJNIEnv();
    JniResource<jclass> pickerClass =
        FindClass(env, "app/rive/runtime/kotlin/fonts/FontFallbackStrategy");
    if (!pickerClass.get())
    {
        RiveLogE("RiveN/FontHelper", "FontFallbackStrategy class not found");
        return ERROR_VEC;
    }

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
    if (!pickerCompanionClass.get())
    {
        RiveLogE("RiveN/FontHelper",
                 "FontFallbackStrategy Companion class not found");
        return ERROR_VEC;
    }

    jmethodID pickFontMid = env->GetMethodID(pickerCompanionClass.get(),
                                             "pickFont",
                                             "(I)Ljava/util/List;");

    // Call the static pickFont method
    JniResource<jobject> fontListObj =
        GetObjectFromMethod(env, companionObject.get(), pickFontMid, weight);

    // Convert Java List<ByteArray> to std::vector<_>
    // Get the List class and methods
    JniResource<jclass> listClass = GetObjectClass(env, fontListObj.get());
    jmethodID listSizeMethod = env->GetMethodID(listClass.get(), "size", "()I");
    jmethodID listGetMethod =
        env->GetMethodID(listClass.get(), "get", "(I)Ljava/lang/Object;");

    jint listSize = JNIExceptionHandler::CallIntMethod(env,
                                                       fontListObj.get(),
                                                       listSizeMethod);

    std::vector<rive::rcp<rive::Font>> decodedFonts;
    decodedFonts.reserve(listSize);

    for (jint i = 0; i < listSize; ++i)
    {
        JniResource<jobject> byteArrayObj =
            GetObjectFromMethod(env, fontListObj.get(), listGetMethod, i);

        // Convert ByteArray to std::vector<uint8_t>
        auto byteArray = reinterpret_cast<jbyteArray>(byteArrayObj.get());
        jsize arrayLength = env->GetArrayLength(byteArray);
        std::vector<uint8_t> byteVector(arrayLength);
        env->GetByteArrayRegion(byteArray,
                                0,
                                arrayLength,
                                reinterpret_cast<jbyte*>(byteVector.data()));

        // Decode once and cache - that saves us from a few other future copies
        rive::rcp<rive::Font> decodedFont = HBFont::Decode(byteVector);
        if (decodedFont)
        {
            decodedFonts.push_back(std::move(decodedFont));
        }
        else
        {
            LOGE("Failed to decode fallback font at index %d for weight %d",
                 i,
                 weight);
        }
    }

    auto [iter, _] = s_pickFontCache.emplace(weight, std::move(decodedFonts));

    return iter->second;
}

std::string FontHelper::UTF8FromCodepoint(rive::Unichar cp)
{
    std::string out;
    out.reserve(4);

    if (cp <= 0x7F)
    {
        out.push_back((char)cp);
    }
    else if (cp <= 0x7FF)
    {
        out.push_back((char)(0xC0 | (cp >> 6)));
        out.push_back((char)(0x80 | (cp & 0x3F)));
    }
    else if (cp <= 0xFFFF)
    {
        out.push_back((char)(0xE0 | (cp >> 12)));
        out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back((char)(0x80 | (cp & 0x3F)));
    }
    else
    { // <= 0x10FFFF
        out.push_back((char)(0xF0 | (cp >> 18)));
        out.push_back((char)(0x80 | ((cp >> 12) & 0x3F)));
        out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back((char)(0x80 | (cp & 0x3F)));
    }

    return out;
}

std::string FontHelper::DebugCodepoint(rive::Unichar cp)
{
    // Control characters can be mapped to their "Control Pictures" equivalent
    if (cp <= 0x20)
    {
        cp += 0x2400;
    }
    // Special case for the delete character
    else if (cp == 0x7F)
    {
        cp = 0x2421;
    }

    return UTF8FromCodepoint(cp);
}

/**
 * Finds and returns a potential fallback font when `riveFont` lacks the
 * `missing` character.
 *
 * This function implements the fallback strategy used when the text shaper
 * encounters a glyph missing in the currently selected font for a text run.
 *
 * Execution Scope: This function is expected to be called from the render
 * thread.
 *
 * Fallback Strategy:
 *
 * 1.  **Weight-Matched Selection:** Uses the weight from `riveFont`
 * (captured on the first attempt where `fallbackIndex == 0`) to retrieve an
 * ordered list of potential fallback fonts via `PickFonts()`. This
 * internal helper interacts with the Kotlin
 * `FontFallbackStrategy.pickFont()` API and will cache decoded fonts
 * (`HBFont`). If `fallbackIndex` is within the bounds of the retrieved
 * list, the font at that index is returned directly. The caller (i.e. the
 * shaper) is attempts shaping the run with the returned font. If it can't,
 * it'll call this function again with an incremented `fallbackIndex`.
 *
 * 2.  **Registered Fallback Search (Deprecated):** If the weight-matched
 * list is exhausted (`fallbackIndex` is out of bounds), iterates through
 * all globally registered fallback fonts (`s_fallbackFonts`). Returns the
 * first registered fallback font that contains the `missing` glyph (checked
 * via `hasGlyph`).
 *
 * 3.  **System Font Fallback:** If no registered fallback contains the
 * glyph, attempts to load a default system font as a last resort using
 * `GetSystemFontBytes()`. If the system font is successfully decoded and
 * contains the `missing` glyph, it is returned.
 *
 * Thread Safety: Access to shared fallback resources and internal state is
 * protected by a mutex (`s_fallbackFontsMutex`). State Preservation: The
 * `desiredWeight` for step 1 is stored statically and updated only when
 * `fallbackIndex` is 0 to ensure consistency across multiple fallback
 * attempts for the same missing glyph sequence.
 *
 * @param missing The Unicode character code that needs a fallback font.
 * @param fallbackIndex The zero-based index indicating the current attempt
 * number.
 * @param riveFont The font that failed to shape the character.
 * @return A reference-counted pointer (`rcp`) to a suitable fallback Font,
 * or `nullptr` if no suitable fallback could be found.
 */
/* static */ rive::rcp<rive::Font> FontHelper::FindFontFallback(
    const rive::Unichar missing,
    const uint32_t fallbackIndex,
    const rive::Font* riveFont)
{
    // Let's lock this down for thread-safety.
    std::lock_guard<std::mutex> lock(s_fallbackFontsMutex);

    // Keep a global variable here so we look for a valid match against the
    // original font weight.
    static uint16_t desiredWeight = 400;
    if (fallbackIndex == 0)
    {
        desiredWeight = riveFont->getWeight();
    }

    const std::vector<rive::rcp<rive::Font>>& pickedFonts =
        PickFonts(desiredWeight);
    if (fallbackIndex < pickedFonts.size())
    {
        return pickedFonts[fallbackIndex];
    }

    // Use the old path - just try to find a match for this glyph.
    for (const rive::rcp<rive::Font>& fFont : s_fallbackFonts)
    {
        if (fFont->hasGlyph(missing))
        {
            return fFont;
        }
    }

    // Nothing in the registered fallbacks? Grab one from the system
    std::vector<uint8_t> fontBytes = FontHelper::GetSystemFontBytes();
    if (fontBytes.empty())
    {
        RiveLogW("RiveN/FontHelper", "FindFontFallback - No system font found");
        return nullptr;
    }

    rive::rcp<rive::Font> systemFont = HBFont::Decode(fontBytes);
    if (!systemFont)
    {
        RiveLogE("RiveN/FontHelper",
                 "FindFontFallback - failed to decode system font bytes");
        return nullptr;
    }

    if (!systemFont->hasGlyph(missing))
    {
        RiveLogE("RiveN/FontHelper", "FindFontFallback - no fallback found");
        return nullptr;
    }

    const std::string glyph = DebugCodepoint(missing);
    RiveLogD(
        "RiveN/FontHelper",
        "FindFontFallback - found a system fallback for missing glyph: U+%04X '%s'",
        missing,
        glyph.c_str());
    return systemFont;
}

} // namespace rive_android