package app.rive.runtime.kotlin.fonts

import java.lang.ref.WeakReference

typealias FontBytes = ByteArray

/**
 * Defines a strategy for providing fallback fonts with a specific weight
 * when a character in a Rive file cannot be found using the current Rive
 * font or if that font is not provided in the Rive file.
 *
 * This interface allows for custom implementations that can provide
 * different sets of fallback fonts based on the specified font weight.
 *
 * ## Strategies for Implementation:
 * - **Within a given scope**: You can implement the strategy in an
 *   activity or fragment as follows:
 *   ```
 *   class MyFontActivity : AppCompatActivity(), FontFallbackStrategy {
 *       override fun onCreate(savedInstanceState: Bundle?) {
 *           super.onCreate(savedInstanceState)
 *           // Set the strategy
 *           FontFallbackStrategy.stylePicker = this
 *       }
 *
 *       override fun getFont(weight: Fonts.Weight): List<FontBytes> {
 *           // Provide font bytes based on the weight
 *       }
 *   }
 *   ```
 *
 * - **At the global level**: You can set the fallback strategy globally
 *   before loading a Rive file that needs fallback fonts:
 *   ```
 *   FontFallbackStrategy.stylePicker = object : FontFallbackStrategy {
 *       override fun getFont(weight: Fonts.Weight): List<ByteArray> {
 *           val myFonts = ...
 *           return listOf(myFonts)
 *       }
 *   }
 *   ```
 *
 * The `stylePicker` is a `WeakReference`, which allows the Garbage
 * Collector to automatically cleanup unused references to the
 * `FontFallbackStrategy`. Once the referenced object is garbage collected,
 * the `WeakReference` will return `null`, ensuring that memory is
 * efficiently managed and released.
 *
 * ## Usage
 * - Implement `getFont()` to return a list of `FontBytes` for the
 *   specified font weight.
 * - Use the companion object to manage the fallback strategy through `stylePicker`.
 * - `pickFont()` is called statically from native C++ code when a missing
 *    character is detected, providing the fallback fonts as needed.
 */

interface FontFallbackStrategy {
    /**
     * Returns a list of `FontBytes` (i.e. `ByteArray`) representing fonts that
     * the runtime will use to try and match a missing character in the Rive
     * file.
     *
     * The runtime attempts to match the character using the fonts in the list
     * in a first-in, first-out (FIFO) order, starting with the font at index 0,
     * then proceeding to index 1, and so on.
     *
     * @param weight The weight of the font to be used for fallback.
     * @return A list of font byte arrays to be used for character fallback.
     */
    fun getFont(weight: Fonts.Weight): List<FontBytes>

    companion object {
        // Use a WeakReference so these can be automatically cleaned up by the JVM.
        private var stylePickerRef: WeakReference<FontFallbackStrategy>? = null

        var stylePicker: FontFallbackStrategy?
            get() = stylePickerRef?.get()
            set(value) {
                if (stylePicker !== value) {
                    stylePickerRef = value?.let { WeakReference(it) }
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