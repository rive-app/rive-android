package app.rive.runtime.kotlin.fonts

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.NativeFontTestHelper
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.TestUtils
import app.rive.runtime.kotlin.test.R
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FontPickerTest {

    private lateinit var context: Context

    /** Loads native bindings and clears fallback state before each test. */
    @Before
    fun setup() {
        context = TestUtils().context // Load library before resetting the native cache.
        FontFallbackStrategy.stylePicker = null
        NativeFontTestHelper.cppCleanupFallbacks() // Reset the fallback state.
    }

    /** Clears Kotlin and native fallback state so subsequent tests cannot inherit it. */
    @After
    fun tearDown() {
        FontFallbackStrategy.stylePicker = null
        NativeFontTestHelper.cppCleanupFallbacks()
    }

    @Test
    @Suppress("DEPRECATION")
    fun noStylePicker() {
        // The font only contains glyphs 'abcdef'
        context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            val fontBytes = it.readBytes()
            // System fallbacks cover Latin, Thai and Han without any setup.
            "uโ你".forEach { char ->
                assertTrue(NativeFontTestHelper.cppFindFontFallback(char.code, fontBytes) >= 0)
            }

            // Find a Thai font and configure fallback system
            val thaiFont = FontHelper.getFallbackFonts(Fonts.FontOpts(lang = "th"))
                .firstOrNull()
                ?: FontHelper.getFallbackFonts().find {
                    it.name.contains("Thai", ignoreCase = true)
                }

            thaiFont?.let { font ->
                assertTrue(
                    Rive.setFallbackFont(Fonts.FontOpts(familyName = font.name)),
                )
                assertTrue(
                    NativeFontTestHelper.cppFindFontFallback("โ".codePointAt(0), fontBytes) >= 0,
                )
            }
        }
    }

    /** Verifies system fallback reuse without weakening per-character coverage checks. */
    @Test
    fun systemFontIsCachedAcrossCharactersAndStrategyResets() {
        context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            assertTrue(NativeFontTestHelper.cppSystemFontIsReused(it.readBytes()))
        }
    }

    /** Verifies that rejected candidates retain only coverage probes and matches are promoted once. */
    @Test
    fun systemFallbackOnlyInitializesMatchingFont() {
        assertTrue(NativeFontTestHelper.cppSystemFallbackProbesBeforePromotion())
    }

    /** Verifies that cached Han selection follows an invalidated locale change, keeping old fonts. */
    @Test
    fun systemFallbackFollowsLocaleChanges() {
        val japanese = FontHelper.getSystemFallbackChain(Locale.JAPAN)
            .firstOrNull { it.path.contains("CJK", ignoreCase = true) }
        val chinese = FontHelper.getSystemFallbackChain(Locale.CHINA)
            .firstOrNull { it.path.contains("CJK", ignoreCase = true) }
        assumeTrue(
            "Requires distinct regional CJK faces",
            japanese != null && chinese != null && japanese != chinese
        )
        val regular = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPAN)
            FontHelper.invalidateSystemFallbacks()
            assertTrue(
                NativeFontTestHelper.cppSystemFallbackChangesWithLocale(
                    regular,
                    Runnable { Locale.setDefault(Locale.CHINA) },
                    Runnable { Locale.setDefault(Locale.JAPAN) },
                    Runnable { FontHelper.invalidateSystemFallbacks() },
                ),
            )
        } finally {
            Locale.setDefault(originalLocale)
            FontHelper.invalidateSystemFallbacks()
        }
    }

    /** Verifies the observer installed by Rive.init changes cached Han selection. */
    @Test
    fun systemFallbackFollowsConfigurationCallbacks() {
        assertSystemFallbackFollowsConfigurationChanges { Rive.init(context) }
    }

    /** Verifies manual native initialization also installs the automatic observer. */
    @Test
    fun manuallyInitializedFallbackFollowsConfigurationCallbacks() {
        // Test setup already loaded the libraries. This path must not need Rive.init again.
        assertSystemFallbackFollowsConfigurationChanges { Rive.initializeCppEnvironment(context) }
    }

    /** Verifies rotation, night mode, and font scale do not invalidate an unchanged locale. */
    @Test
    fun nonLocaleConfigurationChangesPreserveFallbackCaches() {
        withFreshLocaleWatcher(initialize = { Rive.init(context) }) { application ->
            val regular = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
                it.readBytes()
            }
            assertTrue(NativeFontTestHelper.cppFindFontFallback('u'.code, regular) >= 0)
            val generation = NativeFontTestHelper.cppSystemFallbackGeneration()
            val original = context.resources.configuration
            val configurations = listOf(
                Configuration(original).apply {
                    orientation = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        Configuration.ORIENTATION_LANDSCAPE
                    } else {
                        Configuration.ORIENTATION_PORTRAIT
                    }
                },
                Configuration(original).apply {
                    uiMode = uiMode xor Configuration.UI_MODE_NIGHT_MASK
                },
                Configuration(original).apply { fontScale += 0.1f },
            )
            configurations.forEach { configuration ->
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    application.onConfigurationChanged(configuration)
                }
                assertEquals(generation, NativeFontTestHelper.cppSystemFallbackGeneration())
            }
        }
    }

    /** Verifies repeated initialization installs one observer and preserves an unchanged cache. */
    @Test
    fun repeatedInitializationRegistersOneLocaleObserver() {
        withFreshLocaleWatcher(Locale.JAPAN, { Rive.init(context) }) { application ->
            val generation = NativeFontTestHelper.cppSystemFallbackGeneration()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                Rive.init(context)
                Rive.initializeCppEnvironment(context)
            }
            assertEquals(generation, NativeFontTestHelper.cppSystemFallbackGeneration())
            Locale.setDefault(Locale.CHINA)
            dispatchLocaleConfiguration(application)
            assertEquals(generation + 1, NativeFontTestHelper.cppSystemFallbackGeneration())
        }
    }

    /**
     * Exercises the registered Application callback, without explicit font-cache invalidation.
     * @param initialize The public initialization path whose registration is under test.
     */
    private fun assertSystemFallbackFollowsConfigurationChanges(initialize: () -> Unit) {
        val japanese = FontHelper.getSystemFallbackChain(Locale.JAPAN)
            .firstOrNull { it.path.contains("CJK", ignoreCase = true) }
        val chinese = FontHelper.getSystemFallbackChain(Locale.CHINA)
            .firstOrNull { it.path.contains("CJK", ignoreCase = true) }
        assumeTrue(
            "Requires distinct regional CJK faces",
            japanese != null && chinese != null && japanese != chinese,
        )
        val regular = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        withFreshLocaleWatcher(Locale.JAPAN, initialize) { application ->
            assertTrue(
                NativeFontTestHelper.cppSystemFallbackChangesWithLocale(
                    regular,
                    Runnable { Locale.setDefault(Locale.CHINA) },
                    Runnable { Locale.setDefault(Locale.JAPAN) },
                    Runnable { dispatchLocaleConfiguration(application) },
                ),
            )
        }
    }

    /**
     * Delivers a locale configuration through Application's real registered-callback dispatch.
     * @param application The application on which Rive registered its observer.
     */
    private fun dispatchLocaleConfiguration(application: Application) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            application.onConfigurationChanged(
                Configuration(context.resources.configuration).apply {
                    setLocale(Locale.getDefault())
                },
            )
        }
    }

    /**
     * Isolates process-wide observer registration and restores the locale even on test failure.
     * @param locale Default locale at registration time.
     * @param initialize The initialization path to exercise on the main thread.
     * @param block Assertions to run against the application's registered observer.
     */
    private fun withFreshLocaleWatcher(
        locale: Locale = Locale.getDefault(),
        initialize: () -> Unit,
        block: (Application) -> Unit,
    ) {
        val originalLocale = Locale.getDefault()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync {
                FontHelper.stopWatchingLocaleChangesForTesting()
                Locale.setDefault(locale)
                initialize()
            }
            block(context.applicationContext as Application)
        } finally {
            instrumentation.runOnMainSync {
                FontHelper.stopWatchingLocaleChangesForTesting()
                Locale.setDefault(originalLocale)
                Rive.initializeCppEnvironment(context)
            }
        }
    }

    /** Verifies that warming the system cache does not bypass a subsequently installed strategy. */
    @Test
    fun customStrategyTakesPriorityOverCachedSystemFont() {
        val fontBytes = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        assertTrue(NativeFontTestHelper.cppSystemFontIsReused(fontBytes))
        var pickerCalls = 0
        val picker = object : FontFallbackStrategy {
            /** Supplies two candidates to distinguish custom fallback from system fallback. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                pickerCalls++
                return listOf(fontBytes, FontHelper.getFallbackFontBytes()!!)
            }
        }
        FontFallbackStrategy.stylePicker = picker
        assertEquals(1, NativeFontTestHelper.cppFindFontFallback('u'.code, fontBytes))
        assertEquals(1, pickerCalls)
    }

    /** Verifies that a repeated strategy font is listed once, so later fonts stay reachable. */
    @Test
    fun repeatedStrategyFontIsListedOnce() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val picker = object : FontFallbackStrategy {
            /** Repeats the font that lacks 'u' ahead of one that has it. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> =
                listOf(limited, limited.copyOf(), FontHelper.getFallbackFontBytes()!!)
        }
        FontFallbackStrategy.stylePicker = picker
        assertEquals(1, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
        assertEquals(picker, FontFallbackStrategy.stylePicker)
    }

    /** Verifies that variable system fallbacks are instanced at the requested weight. */
    @Test
    fun systemFallbackFollowsWeight() {
        val light = context.resources.openRawResource(R.raw.inter_extralight_onlya).use {
            it.readBytes()
        }
        assertTrue(NativeFontTestHelper.cppSystemFallbackMatchesWeight('你'.code, light))
    }

    /** Verifies that weights served the same bytes share one decoded font. */
    @Test
    fun strategyFontIsDecodedOncePerContent() {
        val regular = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val light = context.resources.openRawResource(R.raw.inter_extralight_onlya).use {
            it.readBytes()
        }
        val requestedWeights = mutableSetOf<Int>()
        val picker = object : FontFallbackStrategy {
            /** Returns a fresh copy each call so sharing can only come from the content. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                requestedWeights.add(weight.weight)
                return listOf(regular.copyOf())
            }
        }
        FontFallbackStrategy.stylePicker = picker
        assertTrue(NativeFontTestHelper.cppStrategyFontIsShared(regular, light))
        assertEquals(2, requestedWeights.size)
        // The strategy is held weakly, so keep it reachable through the native calls.
        assertEquals(picker, FontFallbackStrategy.stylePicker)
    }

    /** Verifies that equal-length hash collisions preserve each font's glyph coverage. */
    @Test
    fun strategyHashCollisionPreservesCoverage() {
        val regular = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val light = context.resources.openRawResource(R.raw.inter_extralight_onlya).use {
            it.readBytes()
        }
        // Trailing padding preserves the font tables while making the length keys identical.
        val size = maxOf(regular.size, light.size)
        assertTrue(
            NativeFontTestHelper.cppStrategyHashCollisionPreservesCoverage(
                regular.copyOf(size),
                light.copyOf(size),
            ),
        )
    }

    @Test
    fun withStylePicker() {
        var isPickerCalled = false
        // Define a style picker.
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<ByteArray> {
                assertEquals(400, weight.weight)
                isPickerCalled = true
                return listOf(byteArrayOf(1, 2, 3))
            }
        }
        FontFallbackStrategy.stylePicker = picker

        // The font only contains glyphs 'abcdef'
        context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            assert(
                NativeFontTestHelper.cppFindFontFallback("u".codePointAt(0), it.readBytes()) >= 0,
            )
            assertTrue(isPickerCalled)
        }
    }

    @Test
    fun withAvailableChars() {
        var isPickerCalled = false
        // Define a style picker..
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<ByteArray> {
                assertEquals(400, weight.weight)
                isPickerCalled = true
                return listOf(byteArrayOf(1, 2, 3))
            }
        }
        FontFallbackStrategy.stylePicker = picker
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
        var pickerWeight = 0

        // Font with the needed characters
        val fontBytes = context
            .resources
            .openRawResource(R.raw.inter_24pt_regular_abcdef)
            .use { it.readBytes() }

        // Define a style picker..
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<ByteArray> {
                pickerCalls++
                pickerWeight = 200
                return listOf(fontBytes)
            }
        }
        FontFallbackStrategy.stylePicker = picker
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
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<ByteArray> {
                pickerCalls++
                assertEquals(200, weight.weight) // ultralight font
                return listOf(fontBytes)
            }
        }
        FontFallbackStrategy.stylePicker = picker
        val file = context
            .resources
            .openRawResource(R.raw.style_fallback_fonts)
            .use { File(it.readBytes()) }

        file.firstArtboard.let { artboard ->
            artboard.setTextRunValue(
                "ultralight_start",
                "abc", // These characters are *not* part of the file.
            )
            artboard.setTextRunValue(
                "ultralight_mid",
                "def", // Neither are these.
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
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                pickerCalls++
                return fontList
            }
        }
        FontFallbackStrategy.stylePicker = picker
        val file = context
            .resources
            .openRawResource(R.raw.style_fallback_fonts)
            .use { File(it.readBytes()) }

        file.firstArtboard.let { artboard ->
            artboard.setTextRunValue(
                "ultralight_start",
                "म अ 错 ا", // All types of different languages
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
            Fonts.FontOpts(lang = "zh-Hans"),
            Fonts.FontOpts(lang = "und-Arab"),
        ).mapNotNull {
            FontHelper.getFallbackFontBytes(it)
        }

        // Define a style picker..
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                pickerCalls++
                return fontList
            }
        }
        FontFallbackStrategy.stylePicker = picker

        val limitedFontBytes =
            context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).readBytes()
        /*
         * A bit of an odd test here: we query our fallback function to get back the index in the
         * Strategy stack found a match against the character in the "म错ا" string.
         * 1. Devangari
         * 2. Chinese
         * 3. Arabic
         */
        "म错ا".codePoints().toArray().forEachIndexed { index, codePoint ->
            assertEquals(
                index,
                NativeFontTestHelper.cppFindFontFallback(
                    codePoint,
                    limitedFontBytes, // just a placeholder...
                ),
            )
        }

        // Cached at the JNI level.
        assertEquals(1, pickerCalls)

        file.release()
        assertFalse(file.hasCppObject) // Cleaned up.
    }
}
