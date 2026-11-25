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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

    /** Verifies that Japanese and Chinese text get their own faces for the same Han character. */
    @Test
    fun systemFallbackFollowsRequestLanguage() {
        val japanese = FontHelper.getSystemFallbackChain(Locale.forLanguageTag("ja"))
            .firstOrNull { it.path.contains("CJK", ignoreCase = true) }
        val chinese = FontHelper.getSystemFallbackChain(Locale.forLanguageTag("zh-Hans"))
            .firstOrNull { it.path.contains("CJK", ignoreCase = true) }
        assumeTrue(
            "Requires distinct regional CJK faces",
            japanese != null && chinese != null && japanese != chinese,
        )
        val regular = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        assertTrue(NativeFontTestHelper.cppSystemFallbackFollowsRequestLanguage(regular))
    }

    /** Verifies the registered request proc shows a strategy each language once. */
    @Test
    fun strategyReceivesRequestLanguages() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val fallback = FontHelper.getFallbackFontBytes()!!
        val requested = mutableListOf<List<String>>()
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<FontBytes> =
                throw AssertionError("The overload with languages replaces this one")

            /** Records the languages it was asked for. */
            override fun getFont(weight: Fonts.Weight, languages: List<String>): List<FontBytes> {
                requested.add(languages)
                return listOf(fallback)
            }
        }
        FontFallbackStrategy.stylePicker = picker
        val japanese = arrayOf("ja-JP", "en-US")
        val chinese = arrayOf("zh-Hans", "en-US")
        assertTrue(NativeFontTestHelper.cppRequestFallback('u'.code, limited, japanese))
        assertTrue(NativeFontTestHelper.cppRequestFallback('u'.code, limited, japanese))
        assertTrue(NativeFontTestHelper.cppRequestFallback('u'.code, limited, chinese))
        assertEquals(listOf(japanese.asList(), chinese.asList()), requested)
        // The strategy is held weakly, so keep it reachable through the native calls.
        assertEquals(picker, FontFallbackStrategy.stylePicker)
    }

    /** Verifies shaped text without a language of its own passes the system languages along. */
    @Test
    fun shapedTextPassesSystemLanguagesToStrategy() {
        val fontBytes = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val requested = mutableListOf<List<String>>()
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<FontBytes> = listOf(fontBytes)

            /** Records the languages it was asked for. */
            override fun getFont(weight: Fonts.Weight, languages: List<String>): List<FontBytes> {
                requested.add(languages)
                return getFont(weight)
            }
        }
        FontFallbackStrategy.stylePicker = picker
        val file = context.resources.openRawResource(R.raw.style_fallback_fonts).use {
            File(it.readBytes())
        }
        try {
            NativeFontHelper.cppSetSystemLanguages(arrayOf("ja-JP", "en-US"))
            file.firstArtboard.let { artboard ->
                artboard.setTextRunValue("ultralight_start", "abc") // Not in the file's font.
                artboard.advance(0f) // Shapes text and asks the strategy for fallbacks.
            }
            assertEquals(listOf(listOf("ja-JP", "en-US")), requested)
        } finally {
            FontHelper.syncSystemLanguages()
            file.release()
        }
        assertEquals(picker, FontFallbackStrategy.stylePicker)
    }

    /** Verifies the default locale reaches native text as its most preferred language. */
    @Test
    fun systemLanguagesFollowDefaultLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPAN)
            FontHelper.invalidateSystemFallbacks()
            assertEquals("ja-JP", NativeFontTestHelper.cppSystemLanguages().firstOrNull())
            Locale.setDefault(Locale.CHINA)
            FontHelper.invalidateSystemFallbacks()
            assertEquals("zh-CN", NativeFontTestHelper.cppSystemLanguages().firstOrNull())
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

    /** A strategy can register a font without deadlocking the native cache. */
    @Test(timeout = 10000)
    fun strategyCanRegisterFallback() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val fallback = FontHelper.getFallbackFontBytes()!!
        val picker = object : FontFallbackStrategy {
            /** Registers fallback bytes from inside the callback. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                Rive.setFallbackFont(fallback)
                return emptyList()
            }
        }
        FontFallbackStrategy.stylePicker = picker
        assertEquals(0, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
        assertEquals(picker, FontFallbackStrategy.stylePicker)
    }

    /** A replacement prevents the old callback from filling the replacement's cache. */
    @Test(timeout = 10000)
    fun strategyCanReplaceItself() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val fallback = FontHelper.getFallbackFontBytes()!!
        var replacementCalls = 0
        val replacement = object : FontFallbackStrategy {
            /** Places a font without coverage first to distinguish this strategy. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                replacementCalls++
                return listOf(limited, fallback)
            }
        }
        val picker = object : FontFallbackStrategy {
            /** Replaces itself before returning obsolete font bytes. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                FontFallbackStrategy.stylePicker = replacement
                return listOf(fallback)
            }
        }
        FontFallbackStrategy.stylePicker = picker
        assertEquals(0, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
        assertEquals(1, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
        assertEquals(1, replacementCalls)
        assertEquals(replacement, FontFallbackStrategy.stylePicker)
    }

    /** The first result cached for a weight and language wins for both concurrent lookups. */
    @Test
    fun concurrentMissesUseFirstCachedResult() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val fallback = FontHelper.getFallbackFontBytes()!!
        val calls = AtomicInteger()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val picker = object : FontFallbackStrategy {
            /** Holds the first callback open so the second can cache its candidates first. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                if (calls.incrementAndGet() == 1) {
                    firstEntered.countDown()
                    releaseFirst.await()
                    return listOf(fallback) // 'u' at index 0
                }
                return listOf(limited, fallback) // 'u' at index 1
            }
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            FontFallbackStrategy.stylePicker = picker
            val first = executor.submit<Int> {
                NativeFontTestHelper.cppFindFontFallback('u'.code, limited)
            }
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS))
            val second = executor.submit<Int> {
                NativeFontTestHelper.cppFindFontFallback('u'.code, limited)
            }
            assertEquals(1, second.get(5, TimeUnit.SECONDS))
            releaseFirst.countDown()
            assertEquals(1, first.get(5, TimeUnit.SECONDS))
            assertEquals(1, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
            assertEquals(2, calls.get())
            // Keep the weakly held picker reachable through the last lookup.
            assertEquals(picker, FontFallbackStrategy.stylePicker)
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    /** Replacing a strategy on another thread invalidates its outstanding callback. */
    @Test(timeout = 15000)
    fun replacementDiscardsConcurrentCallback() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val fallback = FontHelper.getFallbackFontBytes()!!
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var releasedBeforeTimeout = false
        val replacement = object : FontFallbackStrategy {
            /** Makes the replacement's selection distinguishable by its fallback index. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> = listOf(limited, fallback)
        }
        val picker = object : FontFallbackStrategy {
            /** Blocks until the main test thread has installed the replacement. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                entered.countDown()
                releasedBeforeTimeout = release.await(5, TimeUnit.SECONDS)
                return listOf(fallback)
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            FontFallbackStrategy.stylePicker = picker
            val pending = executor.submit<Int> {
                NativeFontTestHelper.cppFindFontFallback('u'.code, limited)
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            FontFallbackStrategy.stylePicker = replacement
            release.countDown()
            assertEquals(0, pending.get(5, TimeUnit.SECONDS))
            // Future.get() makes the callback's write visible to this thread.
            assertTrue("Callback timed out waiting for strategy replacement", releasedBeforeTimeout)
            assertEquals(1, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
            assertEquals(replacement, FontFallbackStrategy.stylePicker)
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    /** Nested shaping uses system fallback without recursively calling the strategy. */
    @Test(timeout = 10000)
    fun strategyCanPerformNestedLookup() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        var calls = 0
        var nestedResult = -1
        val picker = object : FontFallbackStrategy {
            /** Performs another native fallback lookup before returning. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                calls++
                nestedResult = NativeFontTestHelper.cppFindFontFallback('u'.code, limited)
                return emptyList()
            }
        }
        FontFallbackStrategy.stylePicker = picker
        assertEquals(0, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
        assertEquals(0, nestedResult)
        assertEquals(1, calls)
        assertEquals(picker, FontFallbackStrategy.stylePicker)
    }

    /** Verifies that text shaping continues when the strategy throws, and the failure is cached. */
    @Test
    fun throwingStrategyIsCalledOnceAndShapingContinues() {
        var pickerCalls = 0
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                pickerCalls++
                throw IllegalStateException("Strategy failure")
            }
        }
        FontFallbackStrategy.stylePicker = picker
        val file = context.resources.openRawResource(R.raw.style_fallback_fonts).use {
            File(it.readBytes())
        }
        file.firstArtboard.let { artboard ->
            artboard.setTextRunValue("ultralight_start", "abc") // Not in the file's font.
            artboard.advance(0f) // Shapes text and asks the strategy for fallbacks.
            artboard.setTextRunValue("ultralight_start", "cba")
            artboard.advance(0f) // Reshapes text with the cached result.
        }
        assertEquals(1, pickerCalls)
        // The strategy is a weak reference, so keep it reachable until the end.
        assertEquals(picker, FontFallbackStrategy.stylePicker)
        file.release()
    }

    /** Verifies that a null list, which a Java strategy can return, is treated as empty. */
    @Test
    fun nullStrategyListIsTreatedAsEmpty() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        var pickerCalls = 0
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<FontBytes> {
                pickerCalls++
                return uncheckedCast(null)
            }
        }
        FontFallbackStrategy.stylePicker = picker
        // Index 0 past an empty strategy list is a non-strategy fallback.
        assertEquals(0, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
        assertEquals(1, pickerCalls)
        assertEquals(picker, FontFallbackStrategy.stylePicker)
    }

    /** Verifies that a null strategy font is skipped, so later fonts stay reachable. */
    @Test
    fun nullStrategyFontIsSkipped() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val picker = object : FontFallbackStrategy {
            /** Places a null between the font that lacks 'u' and one that has it. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> =
                uncheckedCast(listOf(limited, null, FontHelper.getFallbackFontBytes()!!))
        }
        FontFallbackStrategy.stylePicker = picker
        assertEquals(1, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
        assertEquals(picker, FontFallbackStrategy.stylePicker)
    }

    /** Verifies that a list entry that throws is skipped, so later fonts stay reachable. */
    @Test
    fun throwingStrategyFontIsSkipped() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        val fonts = listOf(limited, null, FontHelper.getFallbackFontBytes()!!)
        val picker = object : FontFallbackStrategy {
            /** Throws for the entry between the font that lacks 'u' and one that has it. */
            override fun getFont(weight: Fonts.Weight): List<FontBytes> =
                object : AbstractList<FontBytes>() {
                    override val size = fonts.size

                    override fun get(index: Int): FontBytes =
                        fonts[index] ?: throw IllegalStateException("Entry failure")
                }
        }
        FontFallbackStrategy.stylePicker = picker
        assertEquals(1, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
        assertEquals(picker, FontFallbackStrategy.stylePicker)
    }

    /** Verifies that a list whose size throws is treated as empty. */
    @Test
    fun throwingStrategyListSizeIsTreatedAsEmpty() {
        val limited = context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).use {
            it.readBytes()
        }
        var sizeCalls = 0
        val picker = object : FontFallbackStrategy {
            override fun getFont(weight: Fonts.Weight): List<FontBytes> =
                object : AbstractList<FontBytes>() {
                    override val size: Int
                        get() {
                            sizeCalls++
                            throw IllegalStateException("Size failure")
                        }

                    override fun get(index: Int): FontBytes = limited
                }
        }
        FontFallbackStrategy.stylePicker = picker
        // Index 0 past an empty strategy list is a non-strategy fallback.
        assertEquals(0, NativeFontTestHelper.cppFindFontFallback('u'.code, limited))
        assertEquals(1, sizeCalls)
        assertEquals(picker, FontFallbackStrategy.stylePicker)
    }

    /**
     * Returns [value] as [T] without a runtime check, to model a Java strategy that ignores Kotlin
     * nullability.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> uncheckedCast(value: Any?): T = value as T

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
