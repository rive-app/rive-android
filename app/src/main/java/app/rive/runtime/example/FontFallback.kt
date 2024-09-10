package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.rive.runtime.example.databinding.ActivityFontFallbackBinding
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.fonts.Fonts

class FontFallback : AppCompatActivity() {

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

        updateTextRun()
        setFallbackFont()
    }

    // Updates the text using the system fallback font.
    private fun updateTextRun() {
        // The riv file consists of two text runs "Hello, world"
        //  and contains only those characters.
        // Using anything different without a fallback font, will result in an empty glyph.
        // For example, try changing the text to something else than 'txt'
        binding.riveViewRoboto.setTextRunValue("Name", "txt")
    }

    // Uses Thai characters after having set the fallback font.
    private fun setFallbackFont() {
        Rive.setFallbackFont(
            Fonts.FontOpts("NotoSansThai-Regular.ttf")
        )

        val thaiText = "โลก"
        binding.riveViewBidi.setTextRunValue("Name", thaiText)
    }
}