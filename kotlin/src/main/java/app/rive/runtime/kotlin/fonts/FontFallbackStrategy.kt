package app.rive.runtime.kotlin.fonts

import app.rive.runtime.kotlin.fonts.FontFallbackStrategy.Companion.stylePicker
import java.lang.ref.WeakReference

typealias FontBytes = ByteArray

/**
 * Defines a strategy for providing fallback fonts with a specific weight when a character in a Rive
 * file cannot be found using the current Rive font or if that font is not provided in the Rive
 * file.
 *
 * This interface allows for custom implementations that can provide different sets of fallback
 * fonts based on the specified font weight.
 *
 * ## Strategies for Implementation:
 * - **Within a given scope**: You can implement the strategy in an activity or fragment as
 *   follows: ``` class MyFontActivity : AppCompatActivity(), FontFallbackStrategy { override fun
 *   onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) // Set the strategy
 *   FontFallbackStrategy.stylePicker = this }
 *
 *   override fun getFont(weight: Fonts.Weight): List<FontBytes> { // Provide font bytes based on
 *   the weight } } ```
 * - **At the global level**: You can set the fallback strategy globally before loading a Rive file
 *   that needs fallback fonts: ``` FontFallbackStrategy.stylePicker = object : FontFallbackStrategy
 *   { override fun getFont(weight: Fonts.Weight): List<ByteArray> { val myFonts = ... return
 *   listOf(myFonts) } } ```
 *
 * The `stylePicker` is a `WeakReference`, which allows the Garbage Collector to automatically
 * cleanup unused references to the `FontFallbackStrategy`. Once the referenced object is garbage
 * collected, the `WeakReference` will return `null`, ensuring that memory is efficiently managed
 * and released.
 *
 * ## Usage
 * - Implement `getFont()` to return a list of `FontBytes` for the specified font weight.
 * - Use the companion object to manage the fallback strategy through `stylePicker`.
 * - `pickFont()` is called statically from native C++ code when a missing character is detected,
 *   providing the fallback fonts as needed.
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
     * cache, and this function **will not be called again for that weight**. The native cache
     * associated with a particular [FontFallbackStrategy] instance is cleared only when a *new*
     * strategy instance is configured for the Rive runtime (i.e. by setting a new [stylePicker]
     * or equivalent configuration). Your implementation should return a complete and ordered list
     * of fallback fonts for the requested `weight` during the initial call, as it represents the
     * definitive set for that weight category weight category while this strategy instance is
     * active.
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
