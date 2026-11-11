/**
 * Testing functions
 */
#ifdef DEBUG

#include <jni.h>
#include <stdint.h>
#include <string>
#include <string_view>
#include <vector>

#include "helpers/font_helper.hpp"
#include "helpers/general.hpp"
#include "helpers/jni_resource.hpp"
#include "helpers/jni_string.hpp"
#include "rive/refcnt.hpp"
#include "rive/span.hpp"
#include "rive/text/font_hb.hpp"
#include "rive/text/text_language.hpp"
#include "rive/text_engine.hpp"

namespace rive_android
{
struct FontHelperTestAccess
{
  /** @return The invalidation generation, without causing a fallback lookup.
   */
  static uint32_t localeGeneration()
  {
    return FontHelper::s_localeGeneration.load(std::memory_order_acquire);
  }

  /**
   * Exercises a cold miss followed by a match without initializing rejected
   * fonts.
   * @return Whether only the selected candidate is promoted and rejected
   * probes are reused.
   */
  static bool systemFallbackProbesBeforePromotion()
  {
    constexpr auto none = rive::TextLanguages::none;
    std::lock_guard<std::mutex> lock(FontHelper::s_fallbackFontsMutex);
    const auto* chain = FontHelper::SystemFallbackChain(none);
    if (!chain)
    {
      return false;
    }
    FontHelper::s_systemFontByCodepoint.clear();
    FontHelper::s_weightInstances.clear();
    for (auto& [_, fallback] : FontHelper::s_systemFonts)
    {
      fallback.font = nullptr;
      fallback.probe.reset();
      fallback.attempted = false;
    }
    if (FontHelper::FindSystemFallback(0x10FFFF, 400, none))
    {
      return false;
    }
    std::vector<HBFont::FileProbe*> probes;
    size_t probeCount = 0;
    for (const auto* fallback : *chain)
    {
      if (fallback->font)
      {
        return false;
      }
      probes.push_back(fallback->probe.get());
      probeCount += fallback->probe != nullptr;
    }
    // Latin is guaranteed by Android and avoids requiring a particular CJK
    // installation.
    auto selected = FontHelper::FindSystemFallback('A', 400, none);
    if (!selected || !selected->hasGlyph('A') || probeCount == 0)
    {
      return false;
    }
    size_t promoted = 0;
    for (size_t i = 0; i < probes.size(); ++i)
    {
      const auto& fallback = *(*chain)[i];
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
           FontHelper::FindSystemFallback('A', 400, none) == selected &&
           !FontHelper::FindSystemFallback(0x10FFFF, 400, none);
  }

  /**
   * @return The system font cached for [missing] in text in [languageTag],
   * or nullptr when there is no such entry.
   */
  static rive::rcp<rive::Font> cachedSystemFallback(
      rive::Unichar missing,
      const std::string& languageTag)
  {
    auto language = rive::TextLanguages::intern(languageTag);
    std::lock_guard<std::mutex> lock(FontHelper::s_fallbackFontsMutex);
    auto entry = FontHelper::s_systemFontByCodepoint.find(
        FontHelper::CodepointKey(missing, language));
    return entry == FontHelper::s_systemFontByCodepoint.end() ? nullptr
                                                              : entry->second;
  }

  /**
   * Forces distinct fonts into one hash bucket on every ABI.
   * @param regularBytes A font containing 'b'.
   * @param lightBytes A font without 'b', padded to the same byte length.
   * @return Whether collisions preserve coverage and identical bytes reuse
   * the matching entry even when it is not the first entry in the bucket.
   */
  static bool collisionPreservesCoverage(
      std::vector<uint8_t> regularBytes,
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
    const size_t hash = std::hash<std::string_view>{}(std::string_view(
        reinterpret_cast<const char*>(regularBytes.data()),
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
    const bool passed = regular && regular != light && regular->hasGlyph('b') &&
                        regular == repeated && bucket.size() == 2;
    cache.clear();
    return passed;
  }
};

/**
 * Asks the registered request proc for a fallback, as the shaper does.
 * @param languages The request's languages, most preferred first.
 * @return The fallback, or nullptr when there is none or no proc is
 * registered.
 */
static rive::rcp<rive::Font> RequestFallback(
    rive::Unichar missing,
    uint32_t fallbackIndex,
    const rive::Font* font,
    const std::vector<std::string>& languages)
{
  if (rive::Font::gFallbackRequestProc == nullptr)
  {
    return nullptr;
  }
  rive::Font::FallbackRequest request = {};
  request.missing = missing;
  request.fallbackIndex = fallbackIndex;
  request.font = font;
  request.languages = languages;
  return rive::Font::gFallbackRequestProc(request);
}
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

  JNIEXPORT jlong JNICALL
  Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppSystemFallbackGeneration(
      JNIEnv*,
      jobject)
  {
    return FontHelperTestAccess::localeGeneration();
  }

  JNIEXPORT jboolean JNICALL
  Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppSystemFallbackChangesWithLocale(
      JNIEnv* env,
      jobject,
      jbyteArray fontBytes,
      jobject changeLocale,
      jobject restoreLocale,
      jobject invalidate)
  {
    auto source = HBFont::Decode(ByteArrayToUint8Vec(env, fontBytes));
    if (!source)
    {
      return JNI_FALSE;
    }
    constexpr rive::Unichar
        han = 0x4E16; // Shared Han character with regional forms.
    auto first = FontHelper::FindFontFallback(han, 0, source.get());
    auto cached = FontHelper::FindFontFallback(han, 0, source.get());
    auto runnableClass = FindClass(env, "java/lang/Runnable");
    auto run = env->GetMethodID(runnableClass.get(), "run", "()V");
    env->CallVoidMethod(changeLocale, run);
    if (env->ExceptionCheck())
    {
      return JNI_FALSE;
    }
    // Without a configuration change the chain holds until invalidated.
    auto stale = FontHelper::FindFontFallback(han, 0, source.get());
    env->CallVoidMethod(invalidate, run);
    if (env->ExceptionCheck())
    {
      return JNI_FALSE;
    }
    auto second = FontHelper::FindFontFallback(han, 0, source.get());
    auto secondCached = FontHelper::FindFontFallback(han, 0, source.get());
    // Retaining first models a shaped run surviving a locale change.
    const bool changed = first && second && first != second && stale == first &&
                         first == cached && second == secondCached &&
                         first->hasGlyph(han) && second->hasGlyph(han);
    env->CallVoidMethod(restoreLocale, run);
    env->CallVoidMethod(invalidate, run);
    if (env->ExceptionCheck())
    {
      return JNI_FALSE;
    }
    auto restored = FontHelper::FindFontFallback(han, 0, source.get());
    // Regular faces should be reused when the chain is reordered again.
    return changed && restored == first;
  }

  JNIEXPORT jboolean JNICALL
  Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppRequestFallback(
      JNIEnv* env,
      jobject,
      jint codePoint,
      jbyteArray fontBytes,
      jobjectArray languages)
  {
    auto font = HBFont::Decode(ByteArrayToUint8Vec(env, fontBytes));
    if (!font)
    {
      return JNI_FALSE;
    }
    return RequestFallback(
               codePoint,
               0,
               font.get(),
               JStringArrayToVec(env, languages)) != nullptr;
  }

  JNIEXPORT jboolean JNICALL
  Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppSystemFallbackFollowsRequestLanguage(
      JNIEnv* env,
      jobject,
      jbyteArray fontBytes)
  {
    auto source = HBFont::Decode(ByteArrayToUint8Vec(env, fontBytes));
    if (!source)
    {
      return JNI_FALSE;
    }
    constexpr rive::Unichar
        han = 0x4E16; // Shared Han character with regional forms.
    auto request = [&](const std::vector<std::string>& languages) {
      return RequestFallback(han, 0, source.get(), languages);
    };
    // The system languages follow the first, as the shaper lists them.
    auto japanese = request({"ja", "en-US"});
    auto chinese = request({"zh-Hans", "en-US"});
    auto japaneseAgain = request({"ja", "en-US"});
    auto chineseAgain = request({"zh-Hans", "en-US"});
    // Both stay cached for the same character, one per language.
    auto cachedJapanese = FontHelperTestAccess::cachedSystemFallback(han, "ja");
    auto cachedChinese = FontHelperTestAccess::cachedSystemFallback(
        han,
        "zh-Hans");
    return japanese && chinese && japanese != chinese &&
           japanese == japaneseAgain && chinese == chineseAgain &&
           japanese->hasGlyph(han) && chinese->hasGlyph(han) &&
           cachedJapanese && cachedChinese && cachedJapanese != cachedChinese;
  }

  JNIEXPORT jobjectArray JNICALL
  Java_app_rive_runtime_kotlin_core_NativeFontTestHelper_cppSystemLanguages(
      JNIEnv* env,
      jobject)
  {
    std::vector<std::string> languages = rive::TextLanguages::systemLanguages();
    auto stringClass = FindClass(env, "java/lang/String");
    jobjectArray out = env->NewObjectArray(
        static_cast<jsize>(languages.size()),
        stringClass.get(),
        nullptr);
    for (size_t i = 0; i < languages.size(); ++i)
    {
      auto tag = MakeJString(env, languages[i]);
      env->SetObjectArrayElement(out, static_cast<jsize>(i), tag.get());
    }
    return out;
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
    auto unsupported = FontHelper::FindFontFallback(
        0x10FFFF,
        0,
        original.get());
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
    std::vector<uint8_t> bytes = ByteArrayToUint8Vec(GetJNIEnv(), fontBytes);
    rive::rcp<rive::Font> aFont = HBFont::Decode(bytes);

    // Try to find a font with the missing code point
    int fallbackIndex = 0;
    rive::rcp<rive::Font> fontWithGlyph = FontHelper::FindFontFallback(
        missingCodePoint,
        fallbackIndex,
        aFont.get());

    // Search through fallbacks until we find one with the glyph or run out
    // of options
    while (fontWithGlyph != nullptr &&
           !fontWithGlyph->hasGlyph(missingCodePoint))
    {
      fallbackIndex++;
      fontWithGlyph = FontHelper::FindFontFallback(
          missingCodePoint,
          fallbackIndex,
          aFont.get());
    }

    LOGI(
        "Font fallback search result: %s",
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
