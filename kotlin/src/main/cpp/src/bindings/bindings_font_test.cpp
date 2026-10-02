/**
 * Testing functions
 */
#ifdef DEBUG

#include <jni.h>
#include <stdint.h>
#include <string_view>
#include <vector>

#include "helpers/font_helper.hpp"
#include "helpers/general.hpp"
#include "helpers/jni_resource.hpp"
#include "rive/refcnt.hpp"
#include "rive/span.hpp"
#include "rive/text/font_hb.hpp"
#include "rive/text_engine.hpp"

namespace rive_android
{
struct FontHelperTestAccess
{
    /**
     * Exercises a cold miss followed by a match without initializing rejected
     * fonts.
     * @return Whether only the selected candidate is promoted and rejected
     * probes are reused.
     */
    static bool systemFallbackProbesBeforePromotion()
    {
        std::lock_guard<std::mutex> lock(FontHelper::s_fallbackFontsMutex);
        if (!FontHelper::RefreshSystemFallbacks())
        {
            return false;
        }
        FontHelper::s_systemFontByCodepoint.clear();
        FontHelper::s_weightInstances.clear();
        for (auto& fallback : FontHelper::s_systemFallbacks)
        {
            fallback.font = nullptr;
            fallback.probe.reset();
            fallback.attempted = false;
        }
        if (FontHelper::FindSystemFallback(0x10FFFF, 400))
        {
            return false;
        }
        std::vector<HBFont::FileProbe*> probes;
        size_t probeCount = 0;
        for (const auto& fallback : FontHelper::s_systemFallbacks)
        {
            if (fallback.font)
            {
                return false;
            }
            probes.push_back(fallback.probe.get());
            probeCount += fallback.probe != nullptr;
        }
        // Latin is guaranteed by Android and avoids requiring a particular CJK
        // installation.
        auto selected = FontHelper::FindSystemFallback('A', 400);
        if (!selected || !selected->hasGlyph('A') || probeCount == 0)
        {
            return false;
        }
        size_t promoted = 0;
        for (size_t i = 0; i < probes.size(); ++i)
        {
            const auto& fallback = FontHelper::s_systemFallbacks[i];
            if (fallback.font)
            {
                ++promoted;
                if (!probes[i] || fallback.probe)
                {
                    return false;
                }
            }
            else if (fallback.probe.get() != probes[i])
            {
                return false;
            }
        }
        return promoted == 1 &&
               FontHelper::FindSystemFallback('A', 400) == selected &&
               !FontHelper::FindSystemFallback(0x10FFFF, 400);
    }

    /**
     * Forces distinct fonts into one hash bucket on every ABI.
     * @param regularBytes A font containing 'b'.
     * @param lightBytes A font without 'b', padded to the same byte length.
     * @return Whether collisions preserve coverage and identical bytes reuse
     * the matching entry even when it is not the first entry in the bucket.
     */
    static bool collisionPreservesCoverage(std::vector<uint8_t> regularBytes,
                                           std::vector<uint8_t> lightBytes)
    {
        if (regularBytes.size() != lightBytes.size())
        {
            return false;
        }
        const std::string_view lightContent(
            reinterpret_cast<const char*>(lightBytes.data()),
            lightBytes.size());
        auto light = HBFont::Decode(std::move(lightBytes));
        if (!light || light->hasGlyph('b'))
        {
            return false;
        }
        const size_t hash = std::hash<std::string_view>{}(
            std::string_view(reinterpret_cast<const char*>(regularBytes.data()),
                             regularBytes.size()));
        auto repeatedBytes = regularBytes;
        std::lock_guard<std::mutex> lock(FontHelper::s_fallbackFontsMutex);
        auto& cache = FontHelper::s_decodedByContent;
        cache.clear();
        // Inject a collision instead of depending on platform-specific hash
        // values, so ARM64 exercises the same failure as 32-bit Android.
        auto& bucket = cache[{regularBytes.size(), hash}];
        bucket.push_back({light, lightContent});
        auto regular = FontHelper::DecodeShared(std::move(regularBytes));
        auto repeated = FontHelper::DecodeShared(std::move(repeatedBytes));
        const bool passed = regular && regular != light &&
                            regular->hasGlyph('b') && regular == repeated &&
                            bucket.size() == 2;
        cache.clear();
        return passed;
    }
};
} // namespace rive_android

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

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppSystemFallbackProbesBeforePromotion(
        JNIEnv*,
        jobject)
    {
        return FontHelperTestAccess::systemFallbackProbesBeforePromotion();
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppSystemFallbackChangesWithLocale(
        JNIEnv* env,
        jobject,
        jbyteArray fontBytes,
        jobject changeLocale,
        jobject restoreLocale)
    {
        auto source = HBFont::Decode(ByteArrayToUint8Vec(env, fontBytes));
        if (!source)
        {
            return JNI_FALSE;
        }
        constexpr rive::Unichar han =
            0x4E16; // Shared Han character with regional forms.
        auto first = FontHelper::FindFontFallback(han, 0, source.get());
        auto cached = FontHelper::FindFontFallback(han, 0, source.get());
        auto runnableClass = FindClass(env, "java/lang/Runnable");
        auto run = env->GetMethodID(runnableClass.get(), "run", "()V");
        env->CallVoidMethod(changeLocale, run);
        if (env->ExceptionCheck())
        {
            return JNI_FALSE;
        }
        auto second = FontHelper::FindFontFallback(han, 0, source.get());
        auto secondCached = FontHelper::FindFontFallback(han, 0, source.get());
        // Retaining first models a shaped run surviving a locale change.
        const bool changed = first && second && first != second &&
                             first == cached && second == secondCached &&
                             first->hasGlyph(han) && second->hasGlyph(han);
        env->CallVoidMethod(restoreLocale, run);
        if (env->ExceptionCheck())
        {
            return JNI_FALSE;
        }
        auto restored = FontHelper::FindFontFallback(han, 0, source.get());
        // Regular faces should be reused when the chain is reordered again.
        return changed && restored == first;
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppSystemFallbackMatchesWeight(
        JNIEnv* env,
        jobject,
        jint codePoint,
        jbyteArray fontBytes)
    {
        auto font = HBFont::Decode(ByteArrayToUint8Vec(env, fontBytes));
        auto fallback = FontHelper::FindFontFallback(codePoint, 0, font.get());
        if (!fallback)
        {
            return JNI_FALSE;
        }
        // Older systems ship static CJK fonts, which cannot follow the weight.
        return !FontHelper::HasWeightAxis(*fallback) ||
               fallback->getWeight() == font->getWeight();
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppStrategyFontIsShared(
        JNIEnv* env,
        jobject,
        jbyteArray regularBytes,
        jbyteArray lightBytes)
    {
        auto regular = HBFont::Decode(ByteArrayToUint8Vec(env, regularBytes));
        auto light = HBFont::Decode(ByteArrayToUint8Vec(env, lightBytes));
        if (!regular || !light || regular->getWeight() == light->getWeight())
        {
            return JNI_FALSE;
        }
        auto forRegular = FontHelper::FindFontFallback('u', 0, regular.get());
        auto forLight = FontHelper::FindFontFallback('u', 0, light.get());
        return forRegular && forRegular == forLight;
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppStrategyHashCollisionPreservesCoverage(
        JNIEnv* env,
        jobject,
        jbyteArray regularBytes,
        jbyteArray lightBytes)
    {
        return FontHelperTestAccess::collisionPreservesCoverage(
            ByteArrayToUint8Vec(env, regularBytes),
            ByteArrayToUint8Vec(env, lightBytes));
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppSystemFontIsReused(
        JNIEnv* env,
        jobject,
        jbyteArray fontBytes)
    {
        auto bytes = ByteArrayToUint8Vec(env, fontBytes);
        auto original = HBFont::Decode(bytes);
        if (!original)
        {
            return JNI_FALSE;
        }

        auto first = FontHelper::FindFontFallback('u', 0, original.get());
        if (!first)
        {
            return JNI_FALSE;
        }
        // Retain first so allocator address reuse cannot hide repeated
        // decoding.
        FontHelper::FindFontFallback('\n', 0, original.get());
        auto unsupported =
            FontHelper::FindFontFallback(0x10FFFF, 0, original.get());
        auto second = FontHelper::FindFontFallback('v', 0, original.get());
        // Strategy changes must preserve the system font.
        FontHelper::resetCache();
        auto afterReset = FontHelper::FindFontFallback('w', 0, original.get());
        return !unsupported && first == second && first == afterReset;
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
