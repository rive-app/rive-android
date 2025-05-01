package app.rive.runtime.kotlin.fonts

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.NativeFontTestHelper
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.TestUtils
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class FontPickerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = TestUtils().context // Load library.
        NativeFontTestHelper.cppCleanupFallbacks() // Reset the fallback state.
    }

    @Test
    fun noStylePicker() {
        FontFallbackStrategy.stylePicker = null
        // The font only contains glyphs 'abcdef'
        context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            val fontBytes = it.readBytes()
            assert(
                // System default has 'u' glyph...
                NativeFontTestHelper.cppFindFontFallback("u".codePointAt(0), fontBytes) >= 0
            )

            assert(
                // ...but not other Unicode (e.g. Thai) characters
                NativeFontTestHelper.cppFindFontFallback("โ".codePointAt(0), fontBytes) < 0
            )

            // Setting a fallback can now find the character
            assertTrue(
                Rive.setFallbackFont(
                    Fonts.FontOpts("NotoSansThai-Regular.ttf")
                )
            )
            assert(
                NativeFontTestHelper.cppFindFontFallback("โ".codePointAt(0), fontBytes) >= 0
            )
        }
    }

    @Test
    fun withStylePicker() {
        var isPickerCalled = false
        // Define a style picker.
        FontFallbackStrategy.stylePicker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<ByteArray> {
                assertEquals(400, weight.weight)
                isPickerCalled = true
                return listOf(byteArrayOf(1, 2, 3))
            }
        }

        // The font only contains glyphs 'abcdef'
        context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            assert(
                NativeFontTestHelper.cppFindFontFallback("u".codePointAt(0), it.readBytes()) >= 0
            )
            assertTrue(isPickerCalled)
        }
    }

    @Test
    fun withAvailableChars() {
        var isPickerCalled = false
        // Define a style picker..
        FontFallbackStrategy.stylePicker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<ByteArray> {
                assertEquals(400, weight.weight)
                isPickerCalled = true
                return listOf(byteArrayOf(1, 2, 3))
            }
        }
        val file = context
            .resources
            .openRawResource(R.raw.style_fallback_fonts)
            .use { File(it.readBytes()) }

        file.firstArtboard.let { artboard ->
            // These characters are baked into the file: don't use fallbacks.
            artboard.setTextRunValue("ultralight_start", "ABC")
            artboard.advance(0f) // reshape text & pick fallbacks if needed.
            assertFalse(isPickerCalled) // Picker was never called.
        }

        file.release()
        assertFalse(file.hasCppObject) // Cleaned up.
    }

    @Test
    fun withUnavailableChars() {
        var pickerCalls = 0
        var pickerWeight = 0;

        // Font with the needed characters
        val fontBytes = context
            .resources
            .openRawResource(R.raw.inter_24pt_regular_abcdef)
            .use { it.readBytes() }

        // Define a style picker..
        FontFallbackStrategy.stylePicker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<ByteArray> {
                pickerCalls++
                pickerWeight = 200
                return listOf(fontBytes)
            }
        }
        val riveFile = context
            .resources
            .openRawResource(R.raw.style_fallback_fonts)
            .use { File(it.readBytes()) }

        riveFile.firstArtboard.let { artboard ->

            artboard.setTextRunValue(
                "ultralight_start",
                "abc", // These characters are *not* part of the file.
            )
            artboard.advance(0f) // shape text & pick fallback.
            assertEquals(1, pickerCalls) // Picker should have been called.
            assertEquals(200, pickerWeight) // ultralight font
        }

        riveFile.release()
        assertFalse(riveFile.hasCppObject) // Cleaned up.
    }

    @Test
    fun withUnavailableIsCached() {
        var pickerCalls = 0

        // Font with the needed characters
        val fontBytes = context
            .resources
            .openRawResource(R.raw.inter_24pt_regular_abcdef)
            .use { it.readBytes() }

        // Define a style picker..
        FontFallbackStrategy.stylePicker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<ByteArray> {
                pickerCalls++
                assertEquals(200, weight.weight) // ultralight font
                return listOf(fontBytes)
            }
        }
        val file = context
            .resources
            .openRawResource(R.raw.style_fallback_fonts)
            .use { File(it.readBytes()) }

        file.firstArtboard.let { artboard ->
            artboard.setTextRunValue(
                "ultralight_start", "abc" // These characters are *not* part of the file.
            )
            artboard.setTextRunValue(
                "ultralight_mid", "def" // Neither are these.
            )
            artboard.advance(0f) // shape text & pick fallback.
            assertEquals(1, pickerCalls)
        }

        file.release()
        assertFalse(file.hasCppObject) // Cleaned up.
    }

    @Test
    fun withMultiLanguageTextRunCached() {
        var pickerCalls = 0

        val fontList = listOf(
            Fonts.FontOpts(lang = "ko"),
            Fonts.FontOpts(lang = "und-Arab"),
            Fonts.FontOpts(lang = "und-Deva"),
            Fonts.FontOpts("NotoSansCJK-Regular.ttc"),
            Fonts.FontOpts("NotoNaskhArabic-Regular.ttf"),
            Fonts.FontOpts("NotoSansDevanagari-VF.ttf"),
        ).mapNotNull {
            FontHelper.getFallbackFontBytes(it)
        }

        // Define a style picker..
        FontFallbackStrategy.stylePicker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                pickerCalls++
                return fontList
            }
        }
        val file = context
            .resources
            .openRawResource(R.raw.style_fallback_fonts)
            .use { File(it.readBytes()) }

        file.firstArtboard.let { artboard ->
            artboard.setTextRunValue(
                "ultralight_start", "म अ 错 ا" // All types of different languages
            )
            artboard.advance(0f) // shape text & pick fallback.

            // Cached at the JNI level.
            assertEquals(1, pickerCalls)
        }

        file.release()
        assertFalse(file.hasCppObject) // Cleaned up.
    }

    @Test
    fun fallbackIndexMatches() {
        val file = context
            .resources
            .openRawResource(R.raw.style_fallback_fonts)
            .use { File(it.readBytes()) }
        var pickerCalls = 0

        val fontList = listOf(
            Fonts.FontOpts(lang = "und-Deva"),
            Fonts.FontOpts("NotoSansCJK-Regular.ttc"),
            Fonts.FontOpts(lang = "und-Arab"),
        ).mapNotNull {
            FontHelper.getFallbackFontBytes(it)
        }

        // Define a style picker..
        FontFallbackStrategy.stylePicker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                pickerCalls++
                return fontList
            }
        }

        /**
         * A bit of an odd test here:
         *  we query our fallback function to get back the index in the Strategy stack found
         *  a match against the character in the "म错ا" string
         *  1. Devangari
         *  2. Chinese
         *  3. Arabic
         */
        "म错ا".codePoints().toArray().forEachIndexed { index, codePoint ->
            assertEquals(
                index,
                NativeFontTestHelper.cppFindFontFallback(
                    codePoint,
                    byteArrayOf() // just a placeholder...
                )
            )
        }

        // Cached at the JNI level.
        assertEquals(1, pickerCalls)

        file.release()
        assertFalse(file.hasCppObject) // Cleaned up.
    }
}
