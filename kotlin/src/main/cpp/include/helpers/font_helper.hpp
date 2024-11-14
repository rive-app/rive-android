//
// Created by Umberto Sonnino on 7/23/24.
//
#ifndef RIVE_ANDROID_FONT_HELPER_HPP
#define RIVE_ANDROID_FONT_HELPER_HPP

#include <vector>
#include "rive/text/font_hb.hpp"

namespace rive_android
{

class FontHelper
{
private:
    static std::vector<rive::rcp<rive::Font>> fallbackFonts;

public:
    static bool registerFallbackFont(jbyteArray);

    static std::vector<uint8_t> getSystemFontBytes();

    static rive::rcp<rive::Font> findFontFallback(
        rive::Span<const rive::Unichar> missing);
};

} // namespace rive_android

#endif // RIVE_ANDROID_FONT_HELPER_HPP
