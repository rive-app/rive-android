#pragma once

#include <atomic>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include "helpers/general.hpp"
#include "rive/text/font_hb.hpp"
#include "rive/text/text_language.hpp"

namespace rive_android
{

class FontHelper
{
private:
  friend struct FontHelperTestAccess;
  // Strategy fonts per weight and request language (see PickKey).
  static std::unordered_map<uint32_t, std::vector<rive::rcp<rive::Font>>>
      s_pickFontCache;
  // The TextLanguages::generation() s_pickFontCache was filled in. Strategies
  // are shown the system languages, so they're asked again once those change.
  static uint32_t s_pickLanguagesGeneration;
  struct StrategyFont
  {
    rive::rcp<rive::Font> font;
    // Non-owning view of the adopted buffer retained by font's HB blob.
    std::string_view content;
  };
  // Hash collisions share a bucket and are resolved against the bytes owned
  // by each font, without retaining another copy of the font data.
  static std::map<std::pair<size_t, size_t>, std::vector<StrategyFont>>
      s_decodedByContent;
  struct SystemFallback
  {
    std::string path;
    unsigned faceIndex;
    rive::rcp<rive::Font> font;
    bool attempted = false;
    // Retain rejected candidates' mapped coverage state without creating
    // Rive metrics, drawing callbacks, or color-glyph state.
    std::unique_ptr<HBFont::FileProbe> probe;
  };
  // Bumped on locale changes so lookups only call into Kotlin after one.
  static std::atomic<uint32_t> s_localeGeneration;
  static uint32_t s_loadedGeneration;
  // Every system font file a chain has listed, by path and face index, so
  // chains for different languages share mapped fonts and probes. Entries are
  // never erased, and map nodes don't move, so chains point into it.
  // Independent of the custom strategy; protected by s_fallbackFontsMutex.
  static std::map<std::pair<std::string, unsigned>, SystemFallback>
      s_systemFonts;
  // System fonts in fallback order for each request language, with
  // TextLanguages::none ordered for the default locale.
  static std::unordered_map<rive::TextLanguageId, std::vector<SystemFallback*>>
      s_systemChains;
  // The system font holding each missing character for each request language
  // (see CodepointKey), null when none does.
  static std::unordered_map<uint64_t, rive::rcp<rive::Font>>
      s_systemFontByCodepoint;
  static std::map<std::pair<const rive::Font*, uint16_t>, rive::rcp<rive::Font>>
      s_weightInstances;
  static std::mutex s_fallbackFontsMutex;

  /** Keys strategy fonts by weight and request language. */
  static uint32_t PickKey(uint16_t weight, rive::TextLanguageId language)
  {
    return (static_cast<uint32_t>(weight) << 8) | language;
  }
  /**
   * Keys a system font selection by character and request language, since
   * Japanese and Chinese text want different faces for the same Han character.
   */
  static uint64_t CodepointKey(
      rive::Unichar missing,
      rive::TextLanguageId language)
  {
    return (static_cast<uint64_t>(language) << 32) | missing;
  }

  /**
   * The strategy's fonts for [weight] and [languages], asking Kotlin on a
   * cache miss. The caller must hold s_fallbackFontsMutex.
   * @param language The slot of languages[0], which keys the cache.
   * @param languages BCP 47 tags passed on to the strategy.
   */
  static const std::vector<rive::rcp<rive::Font>>& PickFonts(
      uint16_t weight,
      rive::TextLanguageId language,
      rive::Span<const std::string> languages);
  /**
   * Reuses an identical strategy font or adopts and decodes its bytes.
   * The caller must hold s_fallbackFontsMutex.
   * @param bytes Font data whose ownership is transferred on a cache miss.
   * @return The decoded font, or nullptr if decoding fails.
   */
  static rive::rcp<rive::Font> DecodeShared(std::vector<uint8_t>&& bytes);
  /**
   * Asks Kotlin for the system font files to try for a missing character.
   * @param languageTag The BCP 47 tag whose families go first, or empty for
   * the default locale.
   * @return Each file's path and face index in fallback order, empty when
   * listing fails.
   */
  static std::vector<std::pair<std::string, unsigned>> ListSystemFallbacks(
      const std::string& languageTag);
  /**
   * The system chain for [language], loaded on first use. After an
   * invalidation, drops every chain and selection before loading again.
   * The caller must hold s_fallbackFontsMutex.
   * @param language The request's language slot, none for the default locale.
   * @return The current chain, or nullptr when no nonempty chain is available.
   */
  static const std::vector<SystemFallback*>* SystemFallbackChain(
      rive::TextLanguageId language);
  static rive::rcp<rive::Font> FindSystemFallback(
      rive::Unichar missing,
      uint16_t weight,
      rive::TextLanguageId language);
  static rive::rcp<rive::Font> AtWeight(
      const rive::rcp<rive::Font>& font,
      uint16_t weight);

  static std::string DebugCodepoint(rive::Unichar cp);
  static std::string UTF8FromCodepoint(rive::Unichar cp);

public:
  static std::vector<rive::rcp<rive::Font>> s_fallbackFonts;

  static bool HasWeightAxis(const rive::Font&);

  /**
   * Clears decoded custom-strategy fonts after a strategy change.
   * System fallback fonts are independent of the strategy and remain
   * cached.
   */
  static void resetCache()
  {
    // Make sure we're not using the cache by locking on that same mutex.
    std::lock_guard<std::mutex> lock(FontHelper::s_fallbackFontsMutex);
    FontHelper::s_pickFontCache.clear();
    FontHelper::s_decodedByContent.clear();
    // Text shaped with the old fallbacks shapes again.
    rive::Font::fallbackFontsChanged();
  }

  static bool RegisterFallbackFont(jbyteArray);

  /** Makes the next fallback lookup reload the locale ordered system chain.
   */
  static void InvalidateSystemFallbacks()
  {
    s_localeGeneration.fetch_add(1, std::memory_order_release);
    rive::Font::fallbackFontsChanged();
  }

  /**
   * Picks a fallback for text in the request's languages. Registered as
   * rive::Font::gFallbackRequestProc.
   */
  static rive::rcp<rive::Font> FindFontFallback(
      const rive::Font::FallbackRequest& request);

  /**
   * Picks a fallback for a request without languages, so the system chain
   * follows the default locale.
   */
  static rive::rcp<rive::Font> FindFontFallback(
      rive::Unichar missing,
      uint32_t fallbackIndex,
      const rive::Font*);
};

} // namespace rive_android
