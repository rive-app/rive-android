package app.rive.runtime.kotlin.fonts

import app.rive.runtime.kotlin.fonts.FontFallbackStrategy.Companion.stylePicker
import java.lang.ref.WeakReference

typealias FontBytes = ByteArray

/**
 * Optionally supplies fallback fonts, per weight, for characters missing from a Rive file's fonts.
 *
 * Most apps don't need one. Without a strategy the runtime falls back through the system fonts,
 * which it maps rather than copies, using the device locale's face for Chinese, Japanese and
 * Korean. Fonts returned here are tried first, so set a strategy only to control how fallback text
 * looks, for example to use a brand font or the same CJK font on every device.
 *
 * ## Guidance
 * - Return fonts your app owns and keep them small, subset to the characters you need. Each one is
 *   copied into native memory, and the system fonts still cover anything they lack.
 * - Don't return system fonts, e.g. from [FontHelper.getFallbackFontBytes]. That copies files the
 *   runtime would otherwise map for free.
 * - Returning the same bytes for several weights is cheap: identical fonts are decoded once.
 * - Set it once, early, and keep a strong reference to it. [stylePicker] is held through a
 *   `WeakReference`, and replacing the strategy discards its decoded fonts.
 *
 * ```
 * class MyApp : Application(), FontFallbackStrategy {
 *     private val brandFont by lazy { assets.open("brand.otf").use { it.readBytes() } }
 *
 *     override fun onCreate() {
 *         super.onCreate()
 *         FontFallbackStrategy.stylePicker = this
 *     }
 *
 *     override fun getFont(weight: Fonts.Weight): List<FontBytes> = listOf(brandFont)
 * }
 * ```
 */

interface FontFallbackStrategy {
    /**
     * Returns a list of `FontBytes` (i.e. `ByteArray`) representing fonts that the runtime will use
     * to try and match a missing character in the Rive file.
     *
     * The runtime attempts to match the character using the fonts in the list in a first-in,
     * first-out (FIFO) order, starting with the font at index 0, then proceeding to index 1, and
     * so on, until a font containing the required character is found or the list is exhausted.
     *
     * **Caching Behavior:** This function is invoked by the Rive runtime only when its internal
     * native cache does not contain fallback font data for the requested `weight`. The returned
     * list of `FontBytes` is immediately decoded into native font representations and cached
     * internally, keyed by the `weight`. Subsequent requests for the *same weight* will use this
     * cache, and this function **will not be called again for that weight**. Fonts with identical
     * bytes are decoded once and shared, so returning the same font for every weight is cheap. The
     * native cache associated with a particular [FontFallbackStrategy] instance is cleared only
     * when a *new* strategy instance is configured for the Rive runtime (i.e. by setting a new
     * [stylePicker] or equivalent configuration). Your implementation should return a complete
     * and ordered list of fallback fonts for the requested `weight` during the initial call, as it
     * represents the definitive set for that weight category while this strategy
     * instance is active.
     *
     * @param weight The weight of the font for which fallbacks are needed.
     * @return A list of font byte arrays to be used for character fallback, ordered by preference.
     */
    fun getFont(weight: Fonts.Weight): List<FontBytes>

    companion object {
        external fun cppResetFontCache()

        // Use a WeakReference so these can be automatically cleaned up by the JVM.
        private var stylePickerRef: WeakReference<FontFallbackStrategy>? = null

        var stylePicker: FontFallbackStrategy?
            get() = stylePickerRef?.get()
            set(value) {
                if (stylePicker !== value) {
                    stylePickerRef = value?.let { WeakReference(it) }
                    cppResetFontCache()
                }
            }

        @Suppress("unused") // Called statically from C++
        fun pickFont(uWeight: Int): List<FontBytes> {
            val sp = stylePicker ?: return emptyList()
            val weight = Fonts.Weight.fromInt(uWeight)
            return sp.getFont(weight)
        }
    }
}
