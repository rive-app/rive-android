package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import app.rive.runtime.example.databinding.ActivityFontFallbackBinding
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import app.rive.runtime.kotlin.fonts.FontBytes
import app.rive.runtime.kotlin.fonts.FontFallbackStrategy
import app.rive.runtime.kotlin.fonts.Fonts

class FontFallback :
    ComponentActivity(),
    FontFallbackStrategy {

    private lateinit var binding: ActivityFontFallbackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFontFallbackBinding.inflate(layoutInflater)
        setEdgeToEdgeContent(binding.root)

        updateTextRuns()
        updateThaiText()

        FontFallbackStrategy.stylePicker = this
    }

    // Read once: identical bytes are decoded once however many weights return them.
    private val appFonts by lazy {
        listOf(R.raw.opensans, R.raw.montserrat).map { id ->
            resources.openRawResource(id).use { it.readBytes() }
        }
    }

    // Draws missing Latin characters in the app's own fonts so they stand out. The Thai view needs
    // no strategy, its glyphs come from the system fonts.
    override fun getFont(weight: Fonts.Weight): List<FontBytes> =
        listOf(if (weight.weight > 400) appFonts[1] else appFonts[0])

    /**
     * The Rive file displayed here contains four blocks of text each with three different runs
     * Also, the Rive file only exported the glyphs that have been specified in the file, and each
     * line is made of three runs: | ABC | DEF | GHI |
     *
     * Modifying these runs with glyphs that are not part of the { ABCDEFGHI } set will require a
     * fallback.
     */
    private fun updateTextRuns() {
        binding.riveViewStylePicker.setTextRunValue("ultralight_start", "aBc ")
        binding.riveViewStylePicker.setTextRunValue("ultralight_mid", "DeF")
        binding.riveViewStylePicker.setTextRunValue("ultralight_end", " gHi")

        binding.riveViewStylePicker.setTextRunValue("regular_mid", " def ")

        binding.riveViewStylePicker.setTextRunValue("bold_start", "PQR ")
        binding.riveViewStylePicker.setTextRunValue("bold_end", "XYZ")

        binding.riveViewStylePicker.setTextRunValue("black_mid", " def ")
    }

    private fun updateThaiText() {
        val thaiHelloText = "สวัสดี "
        val thaiWorldText = " โลก"
        binding.riveViewThai.setTextRunValue("regular_start", thaiHelloText)
        binding.riveViewThai.setTextRunValue("regular_end", thaiWorldText)
    }
}
