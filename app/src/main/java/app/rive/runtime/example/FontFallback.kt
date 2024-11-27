package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.rive.runtime.example.databinding.ActivityFontFallbackBinding
import app.rive.runtime.kotlin.fonts.FontBytes
import app.rive.runtime.kotlin.fonts.FontFallbackStrategy
import app.rive.runtime.kotlin.fonts.FontHelper
import app.rive.runtime.kotlin.fonts.Fonts

class FontFallback : AppCompatActivity(), FontFallbackStrategy {

    private lateinit var binding: ActivityFontFallbackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityFontFallbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        updateTextRuns()
        updateThaiText()

        FontFallbackStrategy.stylePicker = this
    }

    // This class is a `FontFallbackStrategy` and this override picks fonts based on weight.
    override fun getFont(weight: Fonts.Weight): List<FontBytes> {
        var fontMatch = Fonts.FontOpts(
            familyName = "serif",
        )
        when {
            // 'Invert' the weights to make the fallback chars more prominent.
            weight.weight < 400 -> fontMatch =
                Fonts.FontOpts(familyName = "sans-serif", weight = Fonts.Weight(900))
            weight.weight > 400 -> fontMatch =
                Fonts.FontOpts(familyName = "sans-serif", weight = Fonts.Weight(100))
        }
        val fonts = listOf(
            fontMatch,
            // Tag a Thai font along so our second view can draw the glyphs
            Fonts.FontOpts("NotoSansThai-Regular.ttf")        )
        return fonts.mapNotNull { FontHelper.getFallbackFontBytes(it) }
    }

    /**
     * The Rive file displayed here contains four blocks of text each with three different runs
     * Also, the Rive file only exported the glyphs that have been specified in the file, and
     * each line is made of three runs: | ABC | DEF | GHI |     * Modifying these runs with glyphs that are not part of the { ABCDEFGHI } set will require
     * a fallback.
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