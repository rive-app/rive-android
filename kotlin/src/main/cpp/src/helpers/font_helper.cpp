#include "helpers/font_helper.hpp"

#include <algorithm>
#include <string_view>

#include "helpers/general.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/jni_resource.hpp"
#include "helpers/jni_string.hpp"
#include "helpers/rive_log.hpp"

namespace rive_android
{
constexpr auto* TAG = "RiveN/FontHelper";
constexpr uint32_t kWeightAxis = ('w' << 24) | ('g' << 16) | ('h' << 8) | 't';

/* static */ std::vector<rive::rcp<rive::Font>> FontHelper::s_fallbackFonts;
/* static */ std::unordered_map<uint16_t, std::vector<rive::rcp<rive::Font>>>
    FontHelper::s_pickFontCache;
/* static */ std::map<std::pair<size_t, size_t>,
                      std::vector<FontHelper::StrategyFont>>
    FontHelper::s_decodedByContent;
/* static */ std::vector<FontHelper::SystemFallback>
    FontHelper::s_systemFallbacks;
/* static */ std::atomic<uint32_t> FontHelper::s_localeGeneration{1};
/* static */ uint32_t FontHelper::s_loadedGeneration = 0;
/* static */ std::unordered_map<rive::Unichar, rive::rcp<rive::Font>>
    FontHelper::s_systemFontByCodepoint;
/* static */ std::map<std::pair<const rive::Font*, uint16_t>,
                      rive::rcp<rive::Font>>
    FontHelper::s_weightInstances;
/* static */ std::mutex FontHelper::s_fallbackFontsMutex;

/* static */ bool FontHelper::RegisterFallbackFont(jbyteArray byteArray)
{
    rive::rcp<rive::Font> fallback =
        HBFont::Decode(ByteArrayToUint8Vec(GetJNIEnv(), byteArray));
    if (!fallback)
    {
        RiveLogE(TAG, "RegisterFallbackFont - failed to decode byte fonts");
        return false;
    }

    std::lock_guard<std::mutex> lock(s_fallbackFontsMutex);
    s_fallbackFonts.push_back(fallback);
    return true;
}
/* static */ bool FontHelper::RefreshSystemFallbacks()
{
    uint32_t generation = s_localeGeneration.load(std::memory_order_acquire);
    if (generation == s_loadedGeneration && !s_systemFallbacks.empty())
    {
        return true;
    }
    JNIEnv* env = GetJNIEnv();
    // Access is serialized by s_fallbackFontsMutex. Retain the class for the
    // process lifetime so cache hits need no class lookup or string allocation.
    static jclass fontHelperClass = nullptr;
    static jmethodID getChainMethodId = nullptr;
    if (!fontHelperClass)
    {
        auto localClass =
            FindClass(env, "app/rive/runtime/kotlin/fonts/FontHelper");
        if (!localClass.get())
        {
            return false;
        }
        fontHelperClass =
            static_cast<jclass>(env->NewGlobalRef(localClass.get()));
        if (JNIExceptionHandler::ClearAndLogErrors(
                env,
                TAG,
                "Retaining FontHelper class") ||
            !fontHelperClass)
        {
            return false;
        }
    }
    if (!getChainMethodId)
    {
        getChainMethodId = env->GetStaticMethodID(fontHelperClass,
                                                  "getSystemFallbackChain",
                                                  "()Ljava/util/List;");
        if (JNIExceptionHandler::ClearAndLogErrors(
                env,
                TAG,
                "Finding getSystemFallbackChain()") ||
            !getChainMethodId)
        {
            return false;
        }
    }

    JniResource<jobject> chainObj(
        env->CallStaticObjectMethod(fontHelperClass, getChainMethodId),
        env);
    // A missing fallback must not take the app down, so log and carry on.
    if (JNIExceptionHandler::ClearAndLogErrors(
            env,
            TAG,
            "FontHelper couldn't list the system fallback fonts") ||
        !chainObj.get())
    {
        return false;
    }
    JniResource<jclass> fontFileClass =
        FindClass(env, "app/rive/runtime/kotlin/fonts/Fonts$FontFile");

    JniResource<jclass> listClass = GetObjectClass(env, chainObj.get());
    jmethodID listSizeMethod = env->GetMethodID(listClass.get(), "size", "()I");
    jmethodID listGetMethod =
        env->GetMethodID(listClass.get(), "get", "(I)Ljava/lang/Object;");
    jmethodID getPathMethod = env->GetMethodID(fontFileClass.get(),
                                               "getPath",
                                               "()Ljava/lang/String;");
    jmethodID getTtcIndexMethod =
        env->GetMethodID(fontFileClass.get(), "getTtcIndex", "()I");

    jint chainSize =
        JNIExceptionHandler::CallIntMethod(env, chainObj.get(), listSizeMethod);
    std::vector<SystemFallback> fallbacks;
    fallbacks.reserve(chainSize);
    for (jint i = 0; i < chainSize; ++i)
    {
        JniResource<jobject> fontFile =
            GetObjectFromMethod(env, chainObj.get(), listGetMethod, i);
        JniResource<jobject> path =
            GetObjectFromMethod(env, fontFile.get(), getPathMethod);
        jint ttcIndex = JNIExceptionHandler::CallIntMethod(env,
                                                           fontFile.get(),
                                                           getTtcIndexMethod);
        fallbacks.push_back(
            {JStringToString(env, static_cast<jstring>(path.get())),
             static_cast<unsigned>(ttcIndex)});
    }
    if (fallbacks.empty())
    {
        return false;
    }
    for (auto& fallback : fallbacks)
    {
        auto previous =
            std::find_if(s_systemFallbacks.begin(),
                         s_systemFallbacks.end(),
                         [&](const SystemFallback& candidate) {
                             return candidate.path == fallback.path &&
                                    candidate.faceIndex == fallback.faceIndex;
                         });
        if (previous != s_systemFallbacks.end())
        {
            fallback.font = std::move(previous->font);
            fallback.probe = std::move(previous->probe);
            fallback.attempted = previous->attempted;
        }
    }
    // Invalidate both hits and misses before selecting with the new ordering.
    // Existing text retains its own font references, so clearing caches cannot
    // unmap bytes still in use by a shaped run.
    s_systemFontByCodepoint.clear();
    s_weightInstances.clear();
    s_systemFallbacks = std::move(fallbacks);
    s_loadedGeneration = generation;
    return true;
}

/* static */ rive::rcp<rive::Font> FontHelper::FindSystemFallback(
    rive::Unichar missing,
    uint16_t weight)
{
    // No font draws these, and a miss would map every system font.
    if (missing < 0x20 || (missing >= 0x7F && missing < 0xA0) ||
        (missing >= 0x200B && missing <= 0x200F) ||
        (missing >= 0x2028 && missing <= 0x202E) || missing == 0xFEFF)
    {
        return nullptr;
    }
    if (!RefreshSystemFallbacks())
    {
        RiveLogW(TAG, "FindSystemFallback - No current system fonts found");
        return nullptr;
    }

    auto [entry, isNew] = s_systemFontByCodepoint.try_emplace(missing);
    if (isNew)
    {
        for (SystemFallback& fallback : s_systemFallbacks)
        {
            if (!fallback.attempted)
            {
                fallback.probe = HBFont::ProbeFile(fallback.path.c_str(),
                                                   fallback.faceIndex);
                fallback.attempted = true;
            }
            const bool covered =
                fallback.font
                    ? fallback.font->hasGlyph(missing)
                    : fallback.probe && fallback.probe->hasGlyph(missing);
            if (covered)
            {
                if (!fallback.font)
                {
                    // Promote the matching probe without reopening or
                    // remapping.
                    fallback.font = fallback.probe->makeFont();
                    fallback.probe.reset();
                }
                entry->second = fallback.font;
                RiveLogD(TAG,
                         "Fallback for U+%04X '%s': %s face %u",
                         missing,
                         DebugCodepoint(missing).c_str(),
                         fallback.path.c_str(),
                         fallback.faceIndex);
                break;
            }
        }
        if (!entry->second)
        {
            RiveLogD(TAG,
                     "Fallback for U+%04X '%s': no system font has it",
                     missing,
                     DebugCodepoint(missing).c_str());
        }
    }
    return entry->second ? AtWeight(entry->second, weight) : nullptr;
}

/* static */ bool FontHelper::HasWeightAxis(const rive::Font& font)
{
    for (uint16_t i = 0; i < font.getAxisCount(); ++i)
    {
        if (font.getAxis(i).tag == kWeightAxis)
        {
            return true;
        }
    }
    return false;
}

/* static */ rive::rcp<rive::Font> FontHelper::AtWeight(
    const rive::rcp<rive::Font>& font,
    uint16_t weight)
{
    if (!HasWeightAxis(*font) || font->getWeight() == weight)
    {
        return font;
    }

    rive::rcp<rive::Font>& instance = s_weightInstances[{font.get(), weight}];
    if (!instance)
    {
        rive::Font::Coord coord = {kWeightAxis, static_cast<float>(weight)};
        instance =
            font->withOptions(rive::Span<const rive::Font::Coord>(&coord, 1),
                              rive::Span<const rive::Font::Feature>());
    }
    return instance;
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
        RiveLogE(TAG, "FontFallbackStrategy class not found");
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
        RiveLogE(TAG, "FontFallbackStrategy Companion class not found");
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

        rive::rcp<rive::Font> decodedFont = DecodeShared(ByteArrayToUint8Vec(
            env,
            reinterpret_cast<jbyteArray>(byteArrayObj.get())));
        if (!decodedFont)
        {
            RiveLogE(TAG,
                     "Failed to decode fallback font at index %d for weight %d",
                     i,
                     weight);
        }
        // A repeat would end the shaper's walk, which stops when a fallback
        // returns the font it is already using.
        else if (std::find(decodedFonts.begin(),
                           decodedFonts.end(),
                           decodedFont) == decodedFonts.end())
        {
            decodedFonts.push_back(std::move(decodedFont));
        }
    }

    auto [iter, _] = s_pickFontCache.emplace(weight, std::move(decodedFonts));

    return iter->second;
}

/* static */ rive::rcp<rive::Font> FontHelper::DecodeShared(
    std::vector<uint8_t>&& bytes)
{
    // Strategies commonly return the same font for several weights.
    const std::string_view content(reinterpret_cast<const char*>(bytes.data()),
                                   bytes.size());
    size_t hash = std::hash<std::string_view>{}(content);
    auto& bucket = s_decodedByContent[{bytes.size(), hash}];
    for (const auto& entry : bucket)
    {
        if (content == entry.content)
        {
            return entry.font;
        }
    }
    auto font = HBFont::Decode(std::move(bytes));
    if (font)
    {
        // Decode adopts the vector allocation without copying it. Keeping the
        // font alive also keeps content's bytes alive through its HB blob.
        bucket.push_back({font, content});
    }
    return font;
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
 * glyph, returns the first font in `FontHelper.getSystemFallbackChain()` that
 * does, at the desired weight when the font is variable.
 *
 * Thread Safety: Access to shared fallback resources and internal state is
 * protected by a mutex (`s_fallbackFontsMutex`). State Preservation: The
 * `desiredWeight` for steps 1 and 3 is stored statically and updated only when
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

    return FindSystemFallback(missing, desiredWeight);
}

} // namespace rive_android
