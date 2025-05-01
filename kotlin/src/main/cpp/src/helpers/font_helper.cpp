#include "helpers/general.hpp"
#include "helpers/font_helper.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/jni_resource.hpp"

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

/* static */ const std::vector<rive::rcp<rive::Font>>& FontHelper::pick_fonts(
    const uint16_t weight)
{
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
        jbyteArray byteArray = reinterpret_cast<jbyteArray>(byteArrayObj.get());
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
 * 1.  **Weight-Matched Selection:** Uses the weight from `riveFont` (captured
 * on the first attempt where `fallbackIndex == 0`) to retrieve an ordered list
 * of potential fallback fonts via `pick_fonts()`. This internal helper
 * interacts with the Kotlin `FontFallbackStrategy.pickFont()` API and will
 * cache decoded fonts (`HBFont`). If `fallbackIndex` is within the bounds of
 * the retrieved list, the font at that index is returned directly. The caller
 * (i.e. the shaper) is attempts shaping the run with the returned font. If it
 * can't, it'll call this function again with an incremented `fallbackIndex`.
 *
 * 2.  **Registered Fallback Search (Deprecated):** If the weight-matched list
 * is exhausted (`fallbackIndex` is out of bounds), iterates through all
 * globally registered fallback fonts (`s_fallbackFonts`). Returns the first
 * registered fallback font that contains the `missing` glyph (checked via
 * `hasGlyph`).
 *
 * 3.  **System Font Fallback:** If no registered fallback contains the glyph,
 * attempts to load a default system font as a last resort using
 * `GetSystemFontBytes()`. If the system font is successfully decoded and
 * contains the `missing` glyph, it is returned.
 *
 * Thread Safety: Access to shared fallback resources and internal state is
 * protected by a mutex (`s_fallbackFontsMutex`). State Preservation: The
 * `desiredWeight` for step 1 is stored statically and updated only when
 * `fallbackIndex` is 0 to ensure consistency across multiple fallback attempts
 * for the same missing glyph sequence.
 *
 * @param missing The Unicode character code that needs a fallback font.
 * @param fallbackIndex The zero-based index indicating the current attempt
 * number.
 * @param riveFont The font that failed to shape the character.
 * @return A reference-counted pointer (`rcp`) to a suitable fallback Font, or
 *         `nullptr` if no suitable fallback could be found.
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
        pick_fonts(desiredWeight);
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
    return systemFont;
}

} // namespace rive_android