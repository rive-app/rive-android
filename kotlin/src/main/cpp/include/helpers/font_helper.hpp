#pragma once

#include <map>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include "helpers/general.hpp"
#include "rive/text/font_hb.hpp"

namespace rive_android
{

class FontHelper
{
private:
    friend struct FontHelperTestAccess;
    static std::unordered_map<uint16_t, std::vector<rive::rcp<rive::Font>>>
        s_pickFontCache;
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
    // Global reference to the Kotlin list identifying the current locale's
    // chain. Replaced under s_fallbackFontsMutex when the locale changes.
    static jobject s_systemFallbackChain;
    // System fonts in fallback order for the most recently observed locale.
    // Independent of the custom strategy; protected by s_fallbackFontsMutex.
    static std::vector<SystemFallback> s_systemFallbacks;
    // The system font holding each missing character, null when none does.
    static std::unordered_map<rive::Unichar, rive::rcp<rive::Font>>
        s_systemFontByCodepoint;
    static std::map<std::pair<const rive::Font*, uint16_t>,
                    rive::rcp<rive::Font>>
        s_weightInstances;
    static std::mutex s_fallbackFontsMutex;

    static const std::vector<rive::rcp<rive::Font>>& PickFonts(uint16_t weight);
    /**
     * Reuses an identical strategy font or adopts and decodes its bytes.
     * The caller must hold s_fallbackFontsMutex.
     * @param bytes Font data whose ownership is transferred on a cache miss.
     * @return The decoded font, or nullptr if decoding fails.
     */
    static rive::rcp<rive::Font> DecodeShared(std::vector<uint8_t>&& bytes);
    /**
     * Refreshes system selection caches when Kotlin returns a new locale chain.
     * The caller must hold s_fallbackFontsMutex.
     * @return Whether a current, nonempty system fallback chain is available.
     */
    static bool RefreshSystemFallbacks();
    static rive::rcp<rive::Font> FindSystemFallback(rive::Unichar missing,
                                                    uint16_t weight);
    static rive::rcp<rive::Font> AtWeight(const rive::rcp<rive::Font>& font,
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
    }

    static bool RegisterFallbackFont(jbyteArray);

    static rive::rcp<rive::Font> FindFontFallback(rive::Unichar missing,
                                                  uint32_t fallbackIndex,
                                                  const rive::Font*);
};

} // namespace rive_android
