#pragma once

#include <vector>
#include <unordered_map>
#include "helpers/general.hpp"
#include "rive/text/font_hb.hpp"

namespace rive_android
{

class FontHelper
{
private:
    static std::unordered_map<uint16_t, std::vector<rive::rcp<rive::Font>>>
        s_pickFontCache;
    static std::mutex s_fallbackFontsMutex;

    static const std::vector<rive::rcp<rive::Font>>& PickFonts(uint16_t weight);

    static std::string DebugCodepoint(rive::Unichar cp);
    static std::string UTF8FromCodepoint(rive::Unichar cp);

public:
    static std::vector<rive::rcp<rive::Font>> s_fallbackFonts;

    static void resetCache()
    {
        // Make sure we're not using the cache by locking on that same mutex.
        std::lock_guard<std::mutex> lock(FontHelper::s_fallbackFontsMutex);
        FontHelper::s_pickFontCache.clear();
    }

    static bool RegisterFallbackFont(jbyteArray);

    static std::vector<uint8_t> GetSystemFontBytes();

    static rive::rcp<rive::Font> FindFontFallback(rive::Unichar missing,
                                                  uint32_t fallbackIndex,
                                                  const rive::Font*);
};

} // namespace rive_android
