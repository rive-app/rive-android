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
        // The font only contains glyphs 'abcdef'
        context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            val fontBytes = it.readBytes()
            assertTrue(
                // System default has 'u' glyph...
                NativeFontTestHelper.cppFindFontFallback("u".codePointAt(0), fontBytes)
            )

            assertFalse(
                // ...but not other Unicode (e.g. Thai) characters
                NativeFontTestHelper.cppFindFontFallback("โ".codePointAt(0), fontBytes)
            )

            // Setting a fallback can now find the character
            assertTrue(
                Rive.setFallbackFont(
                    Fonts.FontOpts("NotoSansThai-Regular.ttf")
                )
            )
            assertTrue(
                NativeFontTestHelper.cppFindFontFallback("โ".codePointAt(0), fontBytes)
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
            assertTrue(
                NativeFontTestHelper.cppFindFontFallback("u".codePointAt(0), it.readBytes())
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
        // Define a style picker..
        FontFallbackStrategy.stylePicker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<ByteArray> {
                pickerCalls++
                assertEquals(200, weight.weight) // ultralight font
                return listOf(byteArrayOf(1, 2, 3))
            }
        }
        val file = context
            .resources
            .openRawResource(R.raw.style_fallback_fonts)
            .use { File(it.readBytes()) }

        file.firstArtboard.let { artboard ->

            artboard.setTextRunValue(
                "ultralight_start",
                "abc", // These characters are *not* part of the file.
            )
            artboard.advance(0f) // shape text & pick fallback.
            assertEquals(1, pickerCalls) // Picker should have been called.
        }

        file.release()
        assertFalse(file.hasCppObject) // Cleaned up.
    }

    @Test
    fun withUnavailableIsCached() {
        var pickerCalls = 0
        // Define a style picker..
        FontFallbackStrategy.stylePicker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<ByteArray> {
                pickerCalls++
                assertEquals(200, weight.weight) // ultralight font
                return listOf(byteArrayOf(1, 2, 3))
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
}
