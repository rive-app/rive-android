//
// Created by Umberto Sonnino on 7/23/24.
//
#ifndef RIVE_ANDROID_FONT_HELPER_HPP
#define RIVE_ANDROID_FONT_HELPER_HPP

#include <vector>
#include <unordered_map>
#include "helpers/general.hpp"
#include "rive/text/font_hb.hpp"

namespace rive_android
{

class FontHelper
{
private:
    static std::unordered_map<const rive::Font*, rive::rcp<rive::Font>>
        s_fallbackFontsCache;

    static std::vector<std::vector<uint8_t>> pick_fonts(uint16_t weight);

public:
    static std::vector<rive::rcp<rive::Font>> s_fallbackFonts;

    static bool RegisterFallbackFont(jbyteArray);

    static std::vector<uint8_t> GetSystemFontBytes();

    static rive::rcp<rive::Font> FindFontFallback(const rive::Unichar missing,
                                                  const uint32_t fallbackIndex,
                                                  const rive::Font*);
};

} // namespace rive_android

#endif // RIVE_ANDROID_FONT_HELPER_HPP
