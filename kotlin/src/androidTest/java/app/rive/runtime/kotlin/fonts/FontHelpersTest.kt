package app.rive.runtime.kotlin.fonts

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.core.NativeFontTestHelper
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.TestUtils
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

@RunWith(AndroidJUnit4::class)
class FontHelpersTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = TestUtils().context // Load library.
        NativeFontTestHelper.cppCleanupFallbacks() // Reset the fallback state.
    }

    @Test
    fun weightChecks() {
        val default = Fonts.Weight.fromInt()
        assertEquals(default.weight, 400)

        val first = Fonts.Weight.fromString("1000")
        val second = Fonts.Weight.fromString("1000")
        assertEquals(first, second)

        val third = Fonts.Weight.fromInt(1000)
        assertEquals(first, third)
        assertEquals(second, third)

        val oob = Fonts.Weight.fromInt(3000)
        assertEquals(oob, third)
    }

    @Test
    fun getFallbackFontsBasic() {
        context.resources.openRawResource(R.raw.fonts).use { fontStream ->
            val systemFonts = SystemFontsParser.parseFontsXMLMap(fontStream)
            assertTrue(systemFonts.isNotEmpty())

            val defaultFontFromKnownXml = FontHelper.findMatches(
                systemFonts,
                Fonts.FontOpts.DEFAULT
            ).first()

            assertEquals("Roboto-Regular.ttf", defaultFontFromKnownXml.name)
            assertEquals(Fonts.Weight.NORMAL, defaultFontFromKnownXml.weight)
            assertEquals(Fonts.Font.STYLE_NORMAL, defaultFontFromKnownXml.style)
        }

        val fontsFromDevice = FontHelper.getFallbackFonts()
        assertTrue(fontsFromDevice.isNotEmpty())

        val singleFontFromDevice = FontHelper.getFallbackFont()
        assertNotNull(singleFontFromDevice)
        assertEquals(singleFontFromDevice, fontsFromDevice.firstOrNull())
    }

    @Test
    fun getFallbackFontsWithOptions() {
        context.resources.openRawResource(R.raw.fonts).use { fontStream ->
            val systemFonts = SystemFontsParser.parseFontsXMLMap(fontStream)
            assertTrue(systemFonts.isNotEmpty())
            assertTrue(systemFonts.containsKey("serif"))

            val serifFonts = FontHelper.findMatches(
                systemFonts,
                Fonts.FontOpts(familyName = "serif", weight = null, style = null)
            )

            val expectedSerifFontNames = setOf(
                "NotoSerif-Regular.ttf",    // weight 400, style normal
                "NotoSerif-Bold.ttf",       // weight 700, style normal
                "NotoSerif-Italic.ttf",     // weight 400, style italic
                "NotoSerif-BoldItalic.ttf"  // weight 700, style italic
            )
            val actualSerifFontNames = serifFonts.map { it.name }.toSet()

            assertEquals(expectedSerifFontNames.size, serifFonts.size)
            assertEquals(expectedSerifFontNames, actualSerifFontNames)
        }

        // non-existent family against actual device config
        val nonExistentFonts = FontHelper.getFallbackFonts(
            Fonts.FontOpts(familyName = "this-family-doesnt-exist-for-sure-999")
        )
        assertTrue(nonExistentFonts.isEmpty())
    }

    @Test
    fun unnamedFontsDetection() {
        val xmlWithFallbacks = """
    <?xml version="1.0" encoding="utf-8"?>
    <familyset>
        <!-- system fonts -->
        <family name="sans-serif">
            <font weight="400">Roboto-Regular.ttf</font>
            <font weight="700">Roboto-Bold.ttf</font>
        </family>
        
        <!-- fallback fonts (unnamed) -->
        <family lang="und-Arab">
            <font weight="400">NotoNaskhArabic-Regular.ttf</font>
            <font weight="700">NotoNaskhArabic-Bold.ttf</font>
        </family>
        
        <family>
            <font weight="400">DroidSansFallback.ttf</font>
        </family>
    </familyset>
    """.trimIndent()

        val inputStream = ByteArrayInputStream(xmlWithFallbacks.toByteArray(Charsets.UTF_8))
        val families = SystemFontsParser.parseFontsXMLMap(inputStream)

        assertTrue(families.containsKey("sans-serif"))
        assertTrue(families.containsKey("NotoNaskhArabic-Regular.ttf"))
        assertTrue(families.containsKey("DroidSansFallback.ttf"))

        // Family names are preserved
        assertEquals(
            "sans-serif",
            families["sans-serif"]!!.name
        )
        assertEquals(
            "",
            families["NotoNaskhArabic-Regular.ttf"]!!.name
        )
        assertEquals(
            "",
            families["DroidSansFallback.ttf"]!!.name
        )

        // Test that getFallbackFonts() sorts unnamed fonts last
        val allFonts = FontHelper.findMatches(families)
        val fontNames = allFonts.map { it.name }

        // Find indices
        val namedFontIndices = listOf("Roboto-Regular.ttf", "Roboto-Bold.ttf")
            .map { fontNames.indexOf(it) }
            .filter { it >= 0 }

        val unnamedFontIndices = listOf(
            "NotoNaskhArabic-Regular.ttf",
            "NotoNaskhArabic-Bold.ttf",
            "DroidSansFallback.ttf"
        )
            .map { fontNames.indexOf(it) }
            .filter { it >= 0 }

        // Verify all named fonts come before unnamed fonts
        val maxNamedIndex = namedFontIndices.maxOrNull() ?: -1
        val minUnnamedIndex = unnamedFontIndices.minOrNull() ?: Int.MAX_VALUE

        assertTrue(maxNamedIndex < minUnnamedIndex)

        // We have 2 "fallback" families
        val fallbackFamilies = families.values.filter { it.name.isNullOrEmpty() }
        assertEquals(2, fallbackFamilies.size)

        // Test helper method using mock data
        val fallbackFonts = fallbackFamilies
            .flatMap { it.fonts.values.flatten() }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf(
                "NotoNaskhArabic-Regular.ttf",
                "NotoNaskhArabic-Bold.ttf",
                "DroidSansFallback.ttf"
            ),
            fallbackFonts
        )

        // Empty family names return all
        val namelessFamilyFonts = FontHelper.findMatches(
            families,
            opts = Fonts.FontOpts(familyName = "", weight = null, style = null)
        )
        assertEquals(fallbackFonts.size, namelessFamilyFonts.size)
        assertEquals(
            setOf(
                "NotoNaskhArabic-Regular.ttf",
                "NotoNaskhArabic-Bold.ttf",
                "DroidSansFallback.ttf"
            ),
            namelessFamilyFonts.map { it.name }.toSet()
        )
    }

    @Test
    fun getFallbackFontsOrdering() {
        context.resources.openRawResource(R.raw.fonts).use {
            val systemFonts = SystemFontsParser.parseFontsXMLMap(it)

            // Uses FontOpts.DEFAULT
            val fonts = FontHelper.findMatches(systemFonts)
            assertTrue(fonts.isNotEmpty())

            val firstFont = fonts.firstOrNull()
            assertNotNull(firstFont)

            val expectedFirstFontName = "Roboto-Regular.ttf"
            assertEquals(expectedFirstFontName, firstFont?.name)
            assertEquals(Fonts.Weight.NORMAL, firstFont?.weight)
            assertEquals(Fonts.Font.STYLE_NORMAL, firstFont?.style)
        }
    }

    @Test
    fun findMatchesMultipleFonts() {
        context.resources.openRawResource(R.raw.fonts).use { stream ->
            val systemFonts = SystemFontsParser.parseFontsXMLMap(stream)

            val matches =
                FontHelper.findMatches(
                    systemFonts,
                    Fonts.FontOpts(familyName = "sans-serif", style = null, weight = null)
                )
            val expectedSansSerifAll = setOf(
                "Roboto-Thin.ttf", "Roboto-ThinItalic.ttf",
                "Roboto-Light.ttf", "Roboto-LightItalic.ttf",
                "Roboto-Regular.ttf", "Roboto-Italic.ttf",
                "Roboto-Medium.ttf", "Roboto-MediumItalic.ttf",
                "Roboto-Black.ttf", "Roboto-BlackItalic.ttf",
                "Roboto-Bold.ttf", "Roboto-BoldItalic.ttf"
            )
            val actualSansSerifAll = matches.map { it.name }.toSet()
            assertEquals(
                expectedSansSerifAll,
                actualSansSerifAll
            )

            val italicMatches = FontHelper.findMatches(
                systemFonts,
                Fonts.FontOpts(style = Fonts.Font.STYLE_ITALIC, weight = null)
            )
            // All the italic families in the file
            val expectedItalicAll = setOf(
                "Roboto-ThinItalic.ttf", "Roboto-LightItalic.ttf", "Roboto-Italic.ttf",
                "Roboto-MediumItalic.ttf", "Roboto-BlackItalic.ttf", "Roboto-BoldItalic.ttf",
                "RobotoCondensed-LightItalic.ttf", "RobotoCondensed-Italic.ttf",
                "RobotoCondensed-BoldItalic.ttf",
                "NotoSerif-Italic.ttf", "NotoSerif-BoldItalic.ttf"
            )
            assertEquals(
                expectedItalicAll.size, // => 11 values
                italicMatches.size,
            )
            assertEquals(expectedItalicAll, italicMatches.map { it.name }.toSet())
            italicMatches.forEach { font -> assertEquals(Fonts.Font.STYLE_ITALIC, font.style) }

            val sansSerifNormal = FontHelper.findMatches(
                systemFonts,
                Fonts.FontOpts(
                    familyName = "sans-serif",
                    style = Fonts.Font.STYLE_NORMAL,
                    weight = null // Any weight
                )
            )
            val expectedSansSerifNormal = setOf(
                "Roboto-Thin.ttf", "Roboto-Light.ttf", "Roboto-Regular.ttf",
                "Roboto-Medium.ttf", "Roboto-Black.ttf", "Roboto-Bold.ttf"
            )
            assertEquals(
                expectedSansSerifNormal.size, // => 6 vals
                sansSerifNormal.size,
            )
            val actualSansSerifNormal = sansSerifNormal.map { it.name }.toSet()
            assertEquals(
                expectedSansSerifNormal,
                actualSansSerifNormal
            )
            sansSerifNormal.forEach { font -> assertEquals(Fonts.Font.STYLE_NORMAL, font.style) }

            val sansSerifItalic = FontHelper.findMatches(
                systemFonts,
                Fonts.FontOpts(
                    familyName = "sans-serif",
                    style = Fonts.Font.STYLE_ITALIC,
                    weight = null // Any weight
                )
            )
            val expectedSansSerifItalic = setOf(
                "Roboto-ThinItalic.ttf", "Roboto-LightItalic.ttf", "Roboto-Italic.ttf",
                "Roboto-MediumItalic.ttf", "Roboto-BlackItalic.ttf", "Roboto-BoldItalic.ttf"
            )

            assertEquals(
                expectedSansSerifItalic.size, // => 6 values
                sansSerifItalic.size,
            )
            assertEquals(
                expectedSansSerifItalic,
                sansSerifItalic.map { it.name }.toSet(),
            )
            sansSerifItalic.forEach { font -> assertEquals(Fonts.Font.STYLE_ITALIC, font.style) }
        }
    }

    @Test
    fun languagePrioritizationInFontList() {
        val xmlWithMultipleLanguages = """
    <?xml version="1.0" encoding="utf-8"?>
    <familyset>
        <family name="sans-serif">
            <font weight="400">Default.ttf</font>
        </family>
        <family name="lang-specific" lang="ko">
            <font weight="400">Korean.ttf</font>
        </family>
        <family name="backup-lang" lang="ko">
            <font weight="400">KoreanBackup.ttf</font>
        </family>
        <family lang="ko">
            <font weight="400">UnnamedKorean.ttf</font>
        </family>
    </familyset>
    """.trimIndent()

        val inputStream = ByteArrayInputStream(xmlWithMultipleLanguages.toByteArray(Charsets.UTF_8))
        val families = SystemFontsParser.parseFontsXMLMap(inputStream)

        // Get Korean fonts (any weight/style within the 'ko' lang)
        val koreanFonts = FontHelper.findMatches(
            families,
            Fonts.FontOpts(lang = "ko", weight = null, style = null)
        )

        assertEquals(3, koreanFonts.size)

        // Named first...
        assertEquals("Korean.ttf", koreanFonts[0].name)
        // ...from backup-lang...
        assertEquals("KoreanBackup.ttf", koreanFonts[1].name)
        // ...and unnamed is last
        assertEquals("UnnamedKorean.ttf", koreanFonts.last().name)
    }

    @Test
    fun systemFontMatch() {
        context.resources.openRawResource(R.raw.fonts).use { fontStream ->
            val systemFonts = SystemFontsParser.parseFontsXMLMap(fontStream)
            assertTrue(systemFonts.isNotEmpty())

            val defaultFont = FontHelper.findMatches(systemFonts, Fonts.FontOpts.DEFAULT).first()
            assertEquals("Roboto-Regular.ttf", defaultFont.name)
            assertEquals(Fonts.Weight.NORMAL, defaultFont.weight)
            assertEquals(Fonts.Font.STYLE_NORMAL, defaultFont.style)

            val sansSerifFont = FontHelper.findMatches(
                systemFonts,
                Fonts.FontOpts(familyName = "sans-serif")
            ).first()
            assertEquals(defaultFont, sansSerifFont)

            assertTrue(
                FontHelper.findMatches(
                    systemFonts,
                    Fonts.FontOpts(
                        familyName = "fake-family-that-is-not-in-raw-fonts",
                        style = "dapper"
                    )
                ).isEmpty()
            )

            val aliasOpts = Fonts.FontOpts(familyName = "arial", style = "italic")
            val withAlias = FontHelper.findMatches(systemFonts, aliasOpts).first()
            assertEquals("Roboto-Italic.ttf", withAlias.name)
            assertEquals(Fonts.Weight.NORMAL, withAlias.weight)
            assertEquals(Fonts.Font.STYLE_ITALIC, withAlias.style)
        }

        val notFoundOnDevice = FontHelper.getFallbackFont(
            Fonts.FontOpts(
                familyName = "this-family-definitely-doesnt-exist-on-device-123",
                style = "fancy"
            )
        )
        assertNull(notFoundOnDevice)
    }

    @Test
    @Suppress("DEPRECATION")
    fun systemFontFiles() {
        // Warn: keep an eye out for this test - this is testing the target device has all the
        //  fonts that have been parsed from the XML file on its File System. We deem this
        //  assumption true, but this guarantee might not hold on all emulator/devices.
        val systemFonts = FontHelper.getSystemFonts()
        assert(systemFonts.isNotEmpty())
        val families = systemFonts.values
        assert(families.isNotEmpty())

        // Check that those loaded are all valid fonts.
        families.forEach { family ->
            val fontMap = family.fonts
            fontMap.forEach { (_, fontList) ->
                assert(fontList.isNotEmpty())
                fontList.forEach { font ->
                    assertNotNull(FontHelper.getFontFile(font))
                }
            }
        }
    }

    @Test
    fun systemFontBytes() {
        val font = FontHelper.getFallbackFont()
        assertNotNull(font)
        val bytes = FontHelper.getFontBytes(font!!)
        assertNotNull(bytes)

        val withAlias = FontHelper.getFallbackFont(
            Fonts.FontOpts(familyName = "arial")
        )
        assertNotNull(withAlias)
        val aliasBytes = FontHelper.getFontBytes(withAlias!!)
        assertNotNull(aliasBytes)
    }

    @Test
    fun parseFonts() {
        val fontResource = context.resources.openRawResource(R.raw.fonts)
        fontResource.use { stream ->
            val systemFonts = SystemFontsParser.parseFontsXMLMap(stream)
            assertTrue(systemFonts.isNotEmpty())
            listOf(
                Pair(100, "Roboto-Thin.ttf"),
                Pair(100, "Roboto-ThinItalic.ttf"),
                Pair(300, "Roboto-Light.ttf"),
                Pair(300, "Roboto-LightItalic.ttf"),
                Pair(400, "Roboto-Regular.ttf"),
                Pair(400, "Roboto-Italic.ttf"),
                Pair(500, "Roboto-Medium.ttf"),
                Pair(500, "Roboto-MediumItalic.ttf"),
                Pair(900, "Roboto-Black.ttf"),
                Pair(900, "Roboto-BlackItalic.ttf"),
                Pair(700, "Roboto-Bold.ttf"),
                Pair(700, "Roboto-BoldItalic.ttf"),
            ).forEach { (weight, fontName) ->
                assertTrue(systemFonts.containsKey("sans-serif"))

                val fontFamily = systemFonts["sans-serif"]!!
                assertTrue(fontFamily.fonts.isNotEmpty())

                val weightedFonts = fontFamily.fonts[Fonts.Weight(weight)]
                assertTrue(weightedFonts?.size == 2)
                assertNotNull(weightedFonts?.find { it.name == fontName })
            }

            // Aliases
            listOf(
                Fonts.Alias("arial", "sans-serif"),
                Fonts.Alias("helvetica", "sans-serif"),
                Fonts.Alias("tahoma", "sans-serif"),
                Fonts.Alias("verdana", "sans-serif"),
                Fonts.Alias("times", "serif"),
                Fonts.Alias("times new roman", "serif"),
                Fonts.Alias("palatino", "serif"),
                Fonts.Alias("georgia", "serif"),
                Fonts.Alias("baskerville", "serif"),
                Fonts.Alias("goudy", "serif"),
                Fonts.Alias("fantasy", "serif"),
                Fonts.Alias("ITC Stone Serif", "serif"),
                Fonts.Alias("sans-serif-monospace", "monospace"),
                Fonts.Alias("monaco", "monospace"),
                Fonts.Alias("courier", "serif-monospace"),
                Fonts.Alias("courier new", "serif-monospace"),
            )
                .forEach { (aliasName, original) ->
                    assertTrue(systemFonts.containsKey(original))
                    assertTrue(systemFonts.containsKey(aliasName))

                    val originalFamily = systemFonts[original]!!
                    val aliasFamily = systemFonts[aliasName]!!

                    // Check that the alias correctly references the original's content.
                    assertEquals(aliasName, aliasFamily.name)
                    // Check if the underlying map is the same
                    assertEquals(originalFamily.fonts, aliasFamily.fonts)

                    // Check other bits are inherited correctly
                    assertEquals(originalFamily.lang, aliasFamily.lang)
                    assertEquals(originalFamily.variant, aliasFamily.variant)
                }

            // Weighted aliases
            listOf(
                Pair("sans-serif-thin", 100),
                Pair("sans-serif-light", 300),
                Pair("sans-serif-medium", 500),
                Pair("sans-serif-black", 900),
            ).forEach { (alias, weight) ->
                assertTrue(systemFonts.containsKey(alias))

                val fontFamily = systemFonts[alias]!!
                assertEquals(1, fontFamily.fonts.size) // Filtered out other values.

                val weightedFonts = fontFamily.fonts[Fonts.Weight(weight)]
                assertEquals(2, weightedFonts?.size)
            }

            // sans-serif-condensed
            listOf(
                Pair(300, "RobotoCondensed-Light.ttf"),
                Pair(300, "RobotoCondensed-LightItalic.ttf"),
                Pair(400, "RobotoCondensed-Regular.ttf"),
                Pair(400, "RobotoCondensed-Italic.ttf"),
                Pair(700, "RobotoCondensed-Bold.ttf"),
                Pair(700, "RobotoCondensed-BoldItalic.ttf"),
            ).forEach { (weight, fontName) ->
                assertTrue(systemFonts.containsKey("sans-serif-condensed"))

                val fontFamily = systemFonts["sans-serif-condensed"]!!
                assertTrue(fontFamily.fonts.isNotEmpty())

                val weightedFonts = fontFamily.fonts[Fonts.Weight(weight)]
                assertTrue(weightedFonts?.size == 2)
                assertNotNull(weightedFonts?.find { it.name == fontName })
            }

            // serif
            listOf(
                Pair(400, "NotoSerif-Regular.ttf"),
                Pair(400, "NotoSerif-Italic.ttf"),
                Pair(700, "NotoSerif-Bold.ttf"),
                Pair(700, "NotoSerif-BoldItalic.ttf"),
            ).forEach { (weight, fontName) ->
                assertTrue(systemFonts.containsKey("serif"))

                val fontFamily = systemFonts["serif"]!!
                assertTrue(fontFamily.fonts.isNotEmpty())

                val weightedFonts = fontFamily.fonts[Fonts.Weight(weight)]
                assertTrue(weightedFonts?.size == 2)
                assertNotNull(weightedFonts?.find { it.name == fontName })
            }

            // cursive
            listOf(
                Pair(400, "DancingScript-Regular.ttf"),
                Pair(700, "DancingScript-Bold.ttf"),
            ).forEach { (weight, fontName) ->
                assertTrue(systemFonts.containsKey("cursive"))

                val fontFamily = systemFonts["cursive"]!!
                assertTrue(fontFamily.fonts.isNotEmpty())

                val weightedFonts = fontFamily.fonts[Fonts.Weight(weight)]
                assertTrue(weightedFonts?.size == 1)
                assertNotNull(weightedFonts?.find { it.name == fontName })
            }

            // Leftover families with names and single normal weight
            listOf(
                Pair("monospace", "DroidSansMono.ttf"),
                Pair("serif-monospace", "CutiveMono.ttf"),
                Pair("casual", "ComingSoon.ttf"),
            ).forEach { (familyName, fontName) ->
                assertTrue(systemFonts.containsKey(familyName))

                val fontFamily = systemFonts[familyName]!!
                assertTrue(fontFamily.fonts.isNotEmpty())

                val weightedFonts = fontFamily.fonts[Fonts.Weight.NORMAL]
                assertTrue(weightedFonts?.size == 1)
                assertNotNull(weightedFonts?.find { it.name == fontName })
            }

        }
    }

    @Test
    fun parseFontsFallbacks() {
        // The main font file contains a number of fallback fonts that have no explicit family name
        // Our parser uses the name of the first available font as their family name.
        val fontResource = context.resources.openRawResource(R.raw.fonts)
        fontResource.use {
            val systemFonts = SystemFontsParser.parseFontsXMLMap(it)
            assertTrue(systemFonts.isNotEmpty())

            listOf(
                Pair("NotoNaskh-Regular.ttf", "elegant"),
                Pair("NotoNaskhUI-Regular.ttf", "compact"),
                Pair("NotoSansEthiopic-Regular.ttf", null),
                Pair("NotoSansHebrew-Regular.ttf", null),
                Pair("NotoSansThai-Regular.ttf", "elegant"),
                Pair("NotoSansThaiUI-Regular.ttf", "compact"),
                Pair("NotoSansArmenian-Regular.ttf", null),
                Pair("NotoSansGeorgian-Regular.ttf", null),
                Pair("NotoSansDevanagari-Regular.ttf", "elegant"),
                Pair("NotoSansDevanagariUI-Regular.ttf", "compact"),
                Pair("NotoSansGujarati-Regular.ttf", "elegant"),
                Pair("NotoSansGujaratiUI-Regular.ttf", "compact"),
                Pair("NotoSansGurmukhi-Regular.ttf", "elegant"),
                Pair("NotoSansGurmukhiUI-Regular.ttf", "compact"),
                Pair("NotoSansTamil-Regular.ttf", "elegant"),
                Pair("NotoSansTamilUI-Regular.ttf", "compact"),
                Pair("NotoSansMalayalam-Regular.ttf", "elegant"),
                Pair("NotoSansMalayalamUI-Regular.ttf", "compact"),
                Pair("NotoSansBengali-Regular.ttf", "elegant"),
                Pair("NotoSansBengaliUI-Regular.ttf", "compact"),
                Pair("NotoSansTelugu-Regular.ttf", "elegant"),
                Pair("NotoSansTeluguUI-Regular.ttf", "compact"),
                Pair("NotoSansKannada-Regular.ttf", "elegant"),
                Pair("NotoSansKannadaUI-Regular.ttf", "compact"),
                Pair("NotoSansSinhala-Regular.ttf", null),
                Pair("NotoSansKhmer-Regular.ttf", "elegant"),
                Pair("NotoSansKhmerUI-Regular.ttf", "compact"),
                Pair("NotoSansLao-Regular.ttf", "elegant"),
                Pair("NotoSansLaoUI-Regular.ttf", "compact"),
                Pair("NotoSansMyanmar-Regular.ttf", "elegant"),
                Pair("NotoSansMyanmarUI-Regular.ttf", "compact")
            ).forEach { (name, maybeVariant) ->
                assertTrue(systemFonts.containsKey(name))

                val fontFamily = systemFonts[name]!!
                assertEquals(maybeVariant, fontFamily.variant)
                assertTrue(fontFamily.fonts.isNotEmpty())

                val familyFonts = fontFamily.fonts
                assertEquals(2, familyFonts.size)
                val regularFonts = familyFonts[Fonts.Weight.NORMAL]
                assertEquals(1, regularFonts?.size)
                // Same as the family name.
                assertEquals(name, regularFonts?.first()?.name)

                val boldFonts = familyFonts[Fonts.Weight.BOLD]
                assertEquals(1, boldFonts?.size)
                val boldName = name.replaceAfter('-', "Bold.ttf")
                assertEquals(boldName, boldFonts?.first()?.name)
            }

            listOf(
                Pair("NotoSansCherokee-Regular.ttf", null),
                Pair("NotoSansCanadianAboriginal-Regular.ttf", null),
                Pair("NotoSansYi-Regular.ttf", null),
                Pair("NotoSansHans-Regular.otf", "zh-Hans"),
                Pair("NotoSansHant-Regular.otf", "zh-Hant"),
                Pair("NotoSansJP-Regular.otf", "ja"),
                Pair("NotoSansKR-Regular.otf", "ko"),
                Pair("NanumGothic.ttf", null),
                Pair("NotoSansSymbols-Regular-Subsetted.ttf", null),
                Pair("NotoColorEmoji.ttf", null),
                Pair("DroidSansFallback.ttf", null),
                Pair("MTLmr3m.ttf", "ja")
            ).forEach { (name, maybeLanguage) ->
                assertTrue(systemFonts.containsKey(name))

                val fontFamily = systemFonts[name]!!
                assertEquals(fontFamily.lang, maybeLanguage)
                assertTrue(fontFamily.fonts.isNotEmpty())

                val familyFonts = fontFamily.fonts
                assertTrue(familyFonts.size == 1)
                val regularFonts = familyFonts[Fonts.Weight.NORMAL]
                assert(regularFonts?.size == 1)
                assert(regularFonts?.first()?.name == name)
            }
        }
    }

    @Test
    fun findMatch() {
        val fontResource = context.resources.openRawResource(R.raw.fonts)
        fontResource.use {
            val systemFonts = SystemFontsParser.parseFontsXMLMap(it)
            assertTrue(systemFonts.isNotEmpty())

            assertNotNull(FontHelper.findMatches(systemFonts).first())

            assertTrue(
                FontHelper.findMatches(
                    systemFonts,
                    Fonts.FontOpts(familyName = "fake", style = "dapper")
                ).isEmpty()
            )

            val withAlias =
                FontHelper.findMatches(
                    systemFonts,
                    Fonts.FontOpts(familyName = "arial", style = "italic")
                ).first()
            assertEquals(withAlias.name, "Roboto-Italic.ttf")
            assertEquals(withAlias.weight, Fonts.Weight.NORMAL)

            val withLang =
                FontHelper.findMatches(
                    systemFonts,
                    Fonts.FontOpts(lang = "ko")
                ).first()
            assertEquals(withLang.name, "NotoSansKR-Regular.otf")
            assertEquals(withLang.weight, Fonts.Weight.NORMAL)
        }
    }

    @Test
    fun parseBackupFonts() {
        val fontResource = context.resources.openRawResource(R.raw.system_fonts)
        fontResource.use {
            val systemFonts = SystemFontsParser.parseFontsXMLMap(it)
            assertTrue(systemFonts.isNotEmpty())
            listOf(
                "sans-serif",
                "arial",
                "helvetica",
                "tahoma",
                "verdana",
                "sans-serif-light",
                "sans-serif-thin",
                "sans-serif-condensed",
                "sans-serif-medium",
                "sans-serif-black",
                "sans-serif-condensed-light",
                "serif",
                "times",
                "times new roman",
                "palatino",
                "georgia",
                "baskerville",
                "goudy",
                "fantasy",
                "ITC Stone Serif",
                "Droid Sans",
                "monospace",
                "sans-serif-monospace",
                "monaco",
                "serif-monospace",
                "courier",
                "courier new",
                "casual",
                "cursive",
                "sans-serif-smallcaps",
            ).forEach { fontName ->
                assertTrue(systemFonts.containsKey(fontName))
                assertTrue(systemFonts[fontName]!!.fonts.isNotEmpty())
            }
        }
    }

    @Test
    fun parseFallbackFonts() {
        val fontResource = context.resources.openRawResource(R.raw.fallback_fonts)
        fontResource.use {
            val systemFonts = SystemFontsParser.parseFontsXMLMap(it)
            assertTrue(systemFonts.isNotEmpty())
            listOf(
                Pair(
                    "NotoNaskh-Regular.ttf",
                    "NotoNaskh-Bold.ttf",
                ),
                Pair(
                    "NotoNaskhUI-Regular.ttf",
                    "NotoNaskhUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansThai-Regular.ttf",
                    "NotoSansThai-Bold.ttf",
                ),
                Pair(
                    "NotoSansThaiUI-Regular.ttf",
                    "NotoSansThaiUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansDevanagari-Regular.ttf",
                    "NotoSansDevanagari-Bold.ttf",
                ),
                Pair(
                    "NotoSansDevanagariUI-Regular.ttf",
                    "NotoSansDevanagariUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansGujarati-Regular.ttf",
                    "NotoSansGujarati-Bold.ttf",
                ),
                Pair(
                    "NotoSansGujaratiUI-Regular.ttf",
                    "NotoSansGujaratiUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansGurmukhi-Regular.ttf",
                    "NotoSansGurmukhi-Bold.ttf",
                ),
                Pair(
                    "NotoSansGurmukhiUI-Regular.ttf",
                    "NotoSansGurmukhiUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansTamil-Regular.ttf",
                    "NotoSansTamil-Bold.ttf",
                ),
                Pair(
                    "NotoSansTamilUI-Regular.ttf",
                    "NotoSansTamilUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansMalayalam-Regular.ttf",
                    "NotoSansMalayalam-Bold.ttf",
                ),
                Pair(
                    "NotoSansMalayalamUI-Regular.ttf",
                    "NotoSansMalayalamUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansBengali-Regular.ttf",
                    "NotoSansBengali-Bold.ttf",
                ),
                Pair(
                    "NotoSansBengaliUI-Regular.ttf",
                    "NotoSansBengaliUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansTelugu-Regular.ttf",
                    "NotoSansTelugu-Bold.ttf",
                ),
                Pair(
                    "NotoSansTeluguUI-Regular.ttf",
                    "NotoSansTeluguUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansKannada-Regular.ttf",
                    "NotoSansKannada-Bold.ttf",
                ),
                Pair(
                    "NotoSansKannadaUI-Regular.ttf",
                    "NotoSansKannadaUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansKhmer-Regular.ttf",
                    "NotoSansKhmer-Bold.ttf",
                ),
                Pair(
                    "NotoSansKhmerUI-Regular.ttf",
                    "NotoSansKhmerUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansLao-Regular.ttf",
                    "NotoSansLao-Bold.ttf",
                ),
                Pair(
                    "NotoSansLaoUI-Regular.ttf",
                    "NotoSansLaoUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansMyanmar-Regular.ttf",
                    "NotoSansMyanmar-Bold.ttf",
                ),
                Pair(
                    "NotoSansMyanmarUI-Regular.ttf",
                    "NotoSansMyanmarUI-Bold.ttf",
                ),
                Pair(
                    "NotoSansEthiopic-Regular.ttf",
                    "NotoSansEthiopic-Bold.ttf",
                ),
                Pair(
                    "NotoSansHebrew-Regular.ttf",
                    "NotoSansHebrew-Bold.ttf",
                ),
                Pair(
                    "NotoSansArmenian-Regular.ttf",
                    "NotoSansArmenian-Bold.ttf",
                ),
                Pair(
                    "NotoSansGeorgian-Regular.ttf",
                    "NotoSansGeorgian-Bold.ttf",
                ),
                Pair(
                    "NotoSansSinhala-Regular.ttf",
                    "NotoSansSinhala-Bold.ttf",
                ),
            ).forEach { (regular, _) ->
                assertTrue(systemFonts.containsKey(regular))
                val fonts = systemFonts[regular]!!.fonts
                assertTrue(fonts[Fonts.Weight.NORMAL]!!.isNotEmpty())
                assertTrue(fonts[Fonts.Weight.BOLD]!!.isNotEmpty())
            }

            listOf(
                Pair("zh-Hans", "NotoSansHans-Regular.otf"),
                Pair("zh-Hant", "NotoSansHant-Regular.otf"),
                Pair("ja", "NotoSansJP-Regular.otf"),
                Pair("ko", "NotoSansKR-Regular.otf"),
                Pair("ja", "MTLmr3m.ttf"),
            ).forEach { (lang, fontName) ->
                assertTrue(systemFonts.containsKey(fontName))
                val family = systemFonts[fontName]!!
                assertEquals(family.lang, lang)
                val fonts = family.fonts
                assertTrue(fonts[Fonts.Weight.NORMAL]!!.isNotEmpty())
                assertFalse(fonts.containsKey(Fonts.Weight.BOLD))
            }

            listOf(
                "NotoSansCherokee-Regular.ttf",
                "NotoSansCanadianAboriginal-Regular.ttf",
                "NotoSansYi-Regular.ttf",
                "NanumGothic.ttf",
                "NotoSansSymbols-Regular-Subsetted.ttf",
                "NotoColorEmoji.ttf",
                "DroidSansFallback.ttf",
            ).forEach { fontName ->
                assertTrue(systemFonts.containsKey(fontName))
                val fonts = systemFonts[fontName]!!.fonts
                assertTrue(fonts[Fonts.Weight.NORMAL]!!.isNotEmpty())
                assertFalse(fonts.containsKey(Fonts.Weight.BOLD))
            }
        }
    }

    @Test
    fun xmlWhitespace() {
        val systemFontXml = """
<?xml version="1.0" encoding="utf-8"?>
<familyset version="21">
    <family name="sans-serif">
        <font weight="700" style="normal">Roboto-Regular.ttf
            <axis tag="ital" stylevalue="0" />
            <axis tag="wdth" stylevalue="100" />
            <axis tag="wght" stylevalue="700" />
        </font>
    </family>
</familyset>
""".trimIndent()

        val inputStream =
            ByteArrayInputStream(Charset.forName("UTF-16").encode(systemFontXml).array())

        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertEquals(1, result.size)
        val font = result["sans-serif"]?.fonts?.get(Fonts.Weight.BOLD)?.first()
        assertNotNull(font)
        val fontFile = FontHelper.getFontFile(font!!)
        assertNotNull(fontFile)
    }

    @Test
    fun legacyFontNoStyle() {
        val systemFontXml = """
<?xml version="1.0" encoding="utf-8"?>
<familyset version="23">
    <family>
        <font supportedAxes="wght,ital">Roboto-Regular.ttf
            <axis tag="wdth" stylevalue="100" />
        </font>
    </family>
</familyset>
""".trimIndent()

        val inputStream =
            ByteArrayInputStream(Charset.forName("UTF-16").encode(systemFontXml).array())

        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertEquals(1, result.size)
        val font = result["Roboto-Regular.ttf"]?.fonts?.get(Fonts.Weight.NORMAL)?.first()
        assertNotNull(font)
        val fontFile = FontHelper.getFontFile(font!!)
        assertNotNull(fontFile)
    }

    @Test
    fun familyWithNoOptionalAttributes() {
        val systemFontXml = """
<?xml version="1.0" encoding="utf-8"?>
<familyset>
    <family>
        <font weight="400">Roboto-Regular.ttf</font>
    </family>
</familyset>
""".trimIndent()

        val inputStream =
            ByteArrayInputStream(systemFontXml.toByteArray(Charsets.UTF_8))

        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertEquals(1, result.size)

        val family = result["Roboto-Regular.ttf"]
        assertNotNull(family)
        assertNull(family?.lang)
        assertNull(family?.variant)

        val font = family?.fonts?.get(Fonts.Weight.NORMAL)?.first()
        assertNotNull(font)
        assertEquals("Roboto-Regular.ttf", font?.name)
    }

    @Test
    fun familyWithMissingLang() {
        val systemFontXml = """
<?xml version="1.0" encoding="utf-8"?>
<familyset>
    <family variant="compact">
        <font weight="400" style="normal">Roboto-Regular.ttf</font>
    </family>
</familyset>
""".trimIndent()

        val inputStream =
            ByteArrayInputStream(systemFontXml.toByteArray(Charsets.UTF_8))

        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertEquals(1, result.size)

        val family = result["Roboto-Regular.ttf"]
        assertNotNull(family)
        assertNull(family?.lang)
        assertEquals("compact", family?.variant)

        val font = family?.fonts?.get(Fonts.Weight.NORMAL)?.first()
        assertNotNull(font)
        assertEquals("Roboto-Regular.ttf", font?.name)
    }

    @Test
    fun familyWithLangAndVariant() {
        val systemFontXml = """
<?xml version="1.0" encoding="utf-8"?>
<familyset version="23">
    <family name="Roboto" lang="en" variant="compact">
        <font weight="400" style="normal">Roboto-Regular.ttf</font>
        <font weight="700" style="bold">Roboto-Bold.ttf</font>
    </family>
</familyset>
""".trimIndent()

        val inputStream =
            ByteArrayInputStream(systemFontXml.toByteArray(Charsets.UTF_8))

        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertEquals(1, result.size)

        val family = result["Roboto"]
        assertNotNull(family)
        assertEquals("en", family?.lang)
        assertEquals("compact", family?.variant)

        val regularFont = family?.fonts?.get(Fonts.Weight.NORMAL)?.first()
        assertNotNull(regularFont)
        assertEquals("Roboto-Regular.ttf", regularFont?.name)

        val boldFont = family?.fonts?.get(Fonts.Weight.BOLD)?.first()
        assertNotNull(boldFont)
        assertEquals("Roboto-Bold.ttf", boldFont?.name)
    }

    @Test
    fun familyWithNoFonts() {
        val systemFontXml = """
<?xml version="1.0" encoding="utf-8"?>
<familyset>
    <family name="Roboto" lang="en" variant="compact">
    </family>
</familyset>
""".trimIndent()

        val inputStream =
            ByteArrayInputStream(systemFontXml.toByteArray(Charsets.UTF_8))

        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertEquals(0, result.size) // Family should not be included
    }

    @Test
    fun familyIsIgnored() {
        val systemFontXml = """
<?xml version="1.0" encoding="utf-8"?>
<familyset>
    <family name="Roboto" lang="en" variant="compact" ignore="true">
        <font weight="400" style="normal">Roboto-Regular.ttf</font>
    </family>
</familyset>
""".trimIndent()

        val inputStream =
            ByteArrayInputStream(systemFontXml.toByteArray(Charsets.UTF_8))

        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertEquals(0, result.size) // Family should not be included
    }

    @Test
    fun fontWithAxis() {
        val systemFontXml = """
<?xml version="1.0" encoding="utf-8"?>
<familyset version="23">
    <family>
        <font weight="400" style="normal">Roboto-Regular.ttf
            <axis tag="wdth" stylevalue="100" />
            <axis tag="wght" stylevalue="400" />
        </font>
    </family>
</familyset>
""".trimIndent()

        val inputStream =
            ByteArrayInputStream(systemFontXml.toByteArray(Charsets.UTF_8))

        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertEquals(1, result.size)

        val font = result["Roboto-Regular.ttf"]?.fonts?.get(Fonts.Weight.NORMAL)?.first()
        assertNotNull(font)
        assertEquals("Roboto-Regular.ttf", font?.name)

        val axes = font?.axis
        assertNotNull(axes)
        assertEquals(2, axes!!.size)
        assertTrue(axes.any { it.tag == "wdth" && it.styleValue == "100" })
        assertTrue(axes.any { it.tag == "wght" && it.styleValue == "400" })
    }

    @Test
    fun filesetWithLangAndVariant() {
        val systemFontXml = """
<?xml version="1.0" encoding="utf-8"?>
<familyset version="23">
    <family lang="en">
        <fileset>
            <file variant="regular">Roboto-Regular.ttf</file>
            <file variant="bold">Roboto-Bold.ttf</file>
        </fileset>
    </family>
</familyset>
""".trimIndent()

        val inputStream =
            ByteArrayInputStream(systemFontXml.toByteArray(Charsets.UTF_8))

        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertEquals(1, result.size)

        val family = result["Roboto-Regular.ttf"]
        assertNotNull(family)
        assertEquals("en", family?.lang)

        val files = family?.fonts
        val fileValues = files!!.values.flatten()
        assertTrue(fileValues.any { font -> font.name == "Roboto-Regular.ttf" })
        assertTrue(fileValues.any { font -> font.name == "Roboto-Bold.ttf" })
    }

    @Test
    @Suppress("DEPRECATION")
    fun nativeFallbackFontRegistration() {
        val families = FontHelper.getSystemFonts().values
        val allFonts = families.flatMap { family -> family.fonts.values.flatten() }
        assert(allFonts.isNotEmpty())

        allFonts
            .asSequence()
            .forEach { font ->
                val bytes = FontHelper.getFontBytes(font)
                assertNotNull(bytes)
                assertTrue(NativeFontHelper.cppRegisterFallbackFont(bytes!!))
            }
    }

    @Test
    @Suppress("DEPRECATION")
    fun nativeHasGlyphWithBytes() {
        // Since this test fetches the bytes from the device, use a Font that
        //  seems to be found across the board
        val thaiFont = FontHelper.getFallbackFonts(
            Fonts.FontOpts(familyName = "")
        ).find { it.name == "NotoSansThai-Regular.ttf" }
        assertNotNull(thaiFont)
        val fontBytes = FontHelper.getFontBytes(thaiFont!!)
        assertNotNull(fontBytes)
        assertTrue(Rive.setFallbackFont(fontBytes!!))

        val limitedFontBytes =
            context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).readBytes()

        // Noto Thai doesn't have Korean glyphs...
        "우호관계의".forEach { char ->
            val codePoint = char.code
            assert(
                NativeFontTestHelper.cppFindFontFallback(
                    codePoint,
                    limitedFontBytes // just a placeholder...
                ) < 0
            )
        }

        // ...but has Thai glyphs
        "ทุกคนมีสิทธิที่จะได้".forEach { char ->
            val codePoint = char.code
            assert(
                NativeFontTestHelper.cppFindFontFallback(
                    codePoint,
                    limitedFontBytes // just a placeholder...
                ) >= 0
            )
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun nativeHasGlyphWithOpts() {
        // Since this test fetches the bytes from the device, use a Font that
        //  seems to be found across the board
        val thaiFont = FontHelper.getFallbackFonts(
            Fonts.FontOpts(familyName = "")
        ).find { it.name == "NotoSansThai-Regular.ttf" }
        assertNotNull(thaiFont)

        val thaiFontBytes = FontHelper.getFontBytes(thaiFont!!)
        assertNotNull(thaiFontBytes)
        assertTrue(Rive.setFallbackFont(thaiFontBytes!!))

        val limitedFontBytes =
            context.resources.openRawResource(R.raw.inter_24pt_regular_abcdef).readBytes()

        // Noto Thai doesn't have Korean glyphs...
        "우호관계의".forEach { char ->
            val codePoint = char.code
            assert(
                NativeFontTestHelper.cppFindFontFallback(
                    codePoint,
                    limitedFontBytes // just a placeholder...
                ) < 0
            )
        }

        // ...but has Thai glyphs
        "ทุกคนมีสิทธิที่จะได้".forEach { char ->
            val codePoint = char.code
            assert(
                NativeFontTestHelper.cppFindFontFallback(
                    codePoint,
                    limitedFontBytes // just a placeholder...
                ) >= 0
            )
        }
    }

    @Test
    fun nativeSystemsFontHelper() {
        val fontByteArray = NativeFontTestHelper.cppGetSystemFontBytes()
        assertTrue(fontByteArray.isNotEmpty())
    }

    @Test
    fun testParseRecursiveAliases() {
        // Test A -> B -> C resolution
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <familyset>
                <family name="actual-font-family">
                    <font weight="400">ActualFont.ttf</font>
                </family>
                <alias name="alias-b" to="actual-font-family" />
                <alias name="alias-a" to="alias-b" />
            </familyset>
        """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val result = SystemFontsParser.parseFontsXMLMap(inputStream)

        assertEquals("Should contain 3 entries (family + 2 resolved aliases)", 3, result.size)
        assertTrue(result.containsKey("actual-font-family"))
        assertTrue("Alias 'alias-b' should be resolved", result.containsKey("alias-b"))
        assertTrue(
            "Alias 'alias-a' should be resolved through 'alias-b'",
            result.containsKey("alias-a")
        )

        // Verify 'alias-a' points to the correct underlying fonts
        val originalFonts = result["actual-font-family"]!!.fonts
        val aliasAFonts = result["alias-a"]!!.fonts
        assertEquals(originalFonts, aliasAFonts)
        assertEquals("alias-a", result["alias-a"]!!.name) // Check name is correct
    }

    @Test
    fun testParseAliasTargetMissing() {
        val xml = """
             <?xml version="1.0" encoding="utf-8"?>
             <familyset>
                 <family name="real-family">
                     <font weight="400">RealFont.ttf</font>
                 </family>
                 <alias name="bad-alias" to="non-existent-family" />
             </familyset>
         """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val result = SystemFontsParser.parseFontsXMLMap(inputStream)

        assertEquals("Should only contain the real family", 1, result.size)
        assertTrue(result.containsKey("real-family"))
        assertFalse(
            "Alias with missing target should not be added",
            result.containsKey("bad-alias")
        )
    }

    @Test
    fun testParseAliasNameCollision() {
        // If a family and an alias have the same name, the family should take precedence.
        val xml = """
             <?xml version="1.0" encoding="utf-8"?>
             <familyset>
                 <family name="collision-name">
                     <font weight="400">FamilyFont.ttf</font>
                 </family>
                 <family name="another-family">
                     <font weight="400">AnotherFont.ttf</font>
                 </family>
                 <alias name="collision-name" to="another-family" />
             </familyset>
         """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val result = SystemFontsParser.parseFontsXMLMap(inputStream)

        assertEquals("Should contain 2 family entries", 2, result.size)
        assertTrue(result.containsKey("collision-name"))
        assertTrue(result.containsKey("another-family"))

        // 'collision-name' refers to the original family, not the alias target
        val collisionFamily = result["collision-name"]!!
        val fontName = collisionFamily.fonts[Fonts.Weight.NORMAL]?.firstOrNull()?.name
        assertEquals("FamilyFont.ttf", fontName)
    }

    @Test
    fun testParseWeightedAliasTargetFontMissing() {
        // Alias points to a weight that doesn't exist in the target family
        val xml = """
             <?xml version="1.0" encoding="utf-8"?>
             <familyset>
                 <family name="target-family">
                     <font weight="400">Target-Regular.ttf</font>
                 </family>
                 <alias name="weighted-bad" to="target-family" weight="700" />
             </familyset>
         """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val result = SystemFontsParser.parseFontsXMLMap(inputStream)

        assertEquals("Should only contain the target family", 1, result.size)
        assertTrue(result.containsKey("target-family"))
        assertFalse(
            "Weighted alias should not be added if target weight missing",
            result.containsKey("weighted-bad")
        )
    }

    @Test
    fun testParseEmptyFamilyTag() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <familyset>
                <family name="test" /> <!-- Empty family -->
            </familyset>
        """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertTrue("Result map should be empty for empty family tag", result.isEmpty())
    }

    @Test
    fun testParseFamilyWithEmptyFontTag() {
        val xml = """
             <?xml version="1.0" encoding="utf-8"?>
             <familyset>
                 <family name="test">
                     <font weight="400" /> <!-- Empty font tag -->
                 </family>
             </familyset>
         """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        // Current parser throws IllegalStateException for empty filename in readFont,
        // which is caught in readFamily, resulting in the family being skipped.
        val result = SystemFontsParser.parseFontsXMLMap(inputStream)
        assertTrue("Family with only empty font tags should be skipped", result.isEmpty())
    }

    @Test
    fun testParseDuplicateFamilyNames() {
        // The last defined family with a given name should overwrite previous ones
        val xml = """
             <?xml version="1.0" encoding="utf-8"?>
             <familyset>
                 <family name="duplicate">
                     <font weight="400">FirstFont.ttf</font>
                 </family>
                 <family name="other">
                     <font weight="400">OtherFont.ttf</font>
                 </family>
                 <family name="duplicate">
                     <font weight="400">SecondFont.ttf</font>
                 </family>
             </familyset>
         """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val result = SystemFontsParser.parseFontsXMLMap(inputStream)

        assertEquals("Should contain 2 unique family entries", 2, result.size)
        assertTrue(result.containsKey("duplicate"))
        assertTrue(result.containsKey("other"))

        // 'duplicate' contains the font from the *last* definition
        val duplicateFamily = result["duplicate"]!!
        val fontName = duplicateFamily.fonts[Fonts.Weight.NORMAL]?.firstOrNull()?.name
        assertEquals("SecondFont.ttf", fontName)
    }

    @Test
    fun testParseWithXmlComments() {
        val xml = """
             <?xml version="1.0" encoding="utf-8"?>
             <!-- Top level comment -->
             <familyset version="23">
                 <family name="test"> <!-- Family comment -->
                     <font weight="400" style="normal">
                         <!-- Font comment -->
                         TestFont.ttf
                     </font>
                     <font weight="700" style="normal">BoldFont.ttf</font>
                 </family>
                 <!-- Another comment -->
             </familyset>
         """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val result = SystemFontsParser.parseFontsXMLMap(inputStream)

        assertEquals("Should parse 1 family despite comments", 1, result.size)
        assertTrue(result.containsKey("test"))
        val family = result["test"]!!
        assertEquals("Should find 2 fonts", 2, family.fonts.values.flatten().size)
        assertNotNull(family.fonts[Fonts.Weight.NORMAL]?.firstOrNull { it.name == "TestFont.ttf" })
        assertNotNull(family.fonts[Fonts.Weight.BOLD]?.firstOrNull { it.name == "BoldFont.ttf" })
    }

    @Test
    fun testParseNestedFamilyset() {
        val xml = """
             <?xml version="1.0" encoding="utf-8"?>
             <familyset>
                 <family name="outer-family">
                     <font weight="400">OuterFont.ttf</font>
                 </family>
                 <familyset>
                     <family name="inner-family">
                         <font weight="400">InnerFont.ttf</font>
                     </family>
                     <alias name="inner-alias" to="inner-family"/>
                 </familyset>
                 <alias name="outer-alias" to="outer-family"/>
             </familyset>
         """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val result = SystemFontsParser.parseFontsXMLMap(inputStream)

        assertEquals("Should contain 4 entries (2 families, 2 aliases)", 4, result.size)
        assertTrue(result.containsKey("outer-family"))
        assertTrue(result.containsKey("inner-family"))
        assertTrue(result.containsKey("outer-alias"))
        assertTrue(result.containsKey("inner-alias"))

        assertEquals(result["outer-alias"]?.fonts, result["outer-family"]?.fonts)
        assertEquals(result["inner-alias"]?.fonts, result["inner-family"]?.fonts)
    }

    @Test
    fun testConcurrentSystemFontsAccess() {
        FontHelper.resetForTesting()

        val numThreads = 10
        val threads = List(numThreads) {
            Thread {
                val fonts = FontHelper.getSystemFontList()
                assertFalse(fonts.isEmpty())
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val finalFonts = FontHelper.getSystemFontList()
        assertFalse(finalFonts.isEmpty())
    }

    @Test
    fun testMalformedXml() {
        val malformedXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <familyset>
            <family name="broken">
                <font weight="400">BrokenFont.ttf</font>
            <!-- Missing closing tag for family -->
            <family name="valid">
                <font weight="400">ValidFont.ttf</font>
            </family>
        </familyset>
    """.trimIndent()

        val inputStream = ByteArrayInputStream(malformedXml.toByteArray(Charsets.UTF_8))

        // Boom?
        assertThrows(XmlPullParserException::class.java) {
            SystemFontsParser.parseFontsXMLMap(inputStream)
        }
    }

    @Test
    fun testLanguagePrioritization() {
        val xmlWithMultipleLanguages = """
        <?xml version="1.0" encoding="utf-8"?>
        <familyset>
            <family name="sans-serif">
                <font weight="400">Default.ttf</font>
            </family>
            <family name="lang-specific" lang="ko">
                <font weight="400">Korean.ttf</font>
            </family>
            <family name="backup-lang" lang="ko">
                <font weight="400">KoreanBackup.ttf</font>
            </family>
        </familyset>
    """.trimIndent()

        val inputStream = ByteArrayInputStream(xmlWithMultipleLanguages.toByteArray(Charsets.UTF_8))
        val families = SystemFontsParser.parseFontsXMLMap(inputStream)

        // Test that when searching for a Korean font, it finds the first defined one
        val matchedFont = FontHelper.findMatches(
            families,
            Fonts.FontOpts(lang = "ko")
        ).first()

        assertEquals("Korean.ttf", matchedFont.name)
    }

    @Test
    fun testCdataSectionInXml() {
        val xmlWithCdata = """
        <?xml version="1.0" encoding="utf-8"?>
        <familyset>
            <family name="cdata-family">
                <font weight="400"><![CDATA[CdataFont.ttf]]></font>
            </family>
        </familyset>
    """.trimIndent()

        val inputStream = ByteArrayInputStream(xmlWithCdata.toByteArray(Charsets.UTF_8))
        val result = SystemFontsParser.parseFontsXMLMap(inputStream)

        assertTrue(result.containsKey("cdata-family"))
        val font = result["cdata-family"]?.fonts?.get(Fonts.Weight.NORMAL)?.firstOrNull()
        assertNotNull(font)
        assertEquals("CdataFont.ttf", font!!.name)
    }

    @Test
    fun testWeightBoundaries() {
        // Test weight values at the boundaries
        val minWeight = Fonts.Weight.fromInt(0)
        assertEquals(0, minWeight.weight)

        val maxWeight = Fonts.Weight.fromInt(1000)
        assertEquals(1000, maxWeight.weight)

        // Test out-of-bounds weights
        val belowMin = Fonts.Weight.fromInt(-100)
        assertEquals(0, belowMin.weight)

        val aboveMax = Fonts.Weight.fromInt(2000)
        assertEquals(1000, aboveMax.weight)

        // Test string parsing with non-numeric input
        val nonNumeric = Fonts.Weight.fromString("bold")
        assertEquals(400, nonNumeric.weight) // Should default to 400

        val emptyString = Fonts.Weight.fromString("")
        assertEquals(400, emptyString.weight) // Should default to 400
    }

    @Test
    fun testResetFunctionality() {
        // First get system fonts to ensure 1 is populated
        val initialFonts = FontHelper.getSystemFontList()
        assertFalse(initialFonts.isEmpty())

        // Create test instance to access the reset method
        FontHelper.resetForTesting()

        // Force reload by creating a mock font XML
        val mockXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <familyset>
            <family name="test-reset">
                <font weight="400">ResetTest.ttf</font>
            </family>
        </familyset>
    """.trimIndent()

        val inputStream = ByteArrayInputStream(mockXml.toByteArray(Charsets.UTF_8))

        // Inject our mock parser
        val mockResult = SystemFontsParser.parseFontsXMLMap(inputStream)

        // Test that after reset, we can load different fonts
        assertTrue(mockResult.containsKey("test-reset"))
    }

    @Test
    fun parseDetailedFontAttributes() {
        val xmlWithNewAttrs = """
    <?xml version="1.0" encoding="utf-8"?>
    <familyset version="23">
      <!-- Unnamed family (no 'name' attribute on <family>), lang specified -->
      <family lang="zh-Hans"> 
        <font weight="400" style="normal" index="2" postScriptName="SECCJKjp-Regular">
          SECCJK-Regular.ttc
        </font>
        <font weight="700" style="normal" index="3" fallbackFor="serif" postScriptName="NotoSerifCJKjp-Bold">
          NotoSerifCJK-Bold.ttc
        </font>
      </family>
      <!-- Named family -->
      <family name="test-family-with-simple-font">
        <font weight="400" style="normal">SimpleFont.ttf</font>
      </family>
    </familyset>
    """.trimIndent()

        val inputStream = ByteArrayInputStream(xmlWithNewAttrs.toByteArray(Charsets.UTF_8))
        val families = SystemFontsParser.parseFontsXMLMap(inputStream)

        val simpleFamily = families["test-family-with-simple-font"]
        assertNotNull(simpleFamily)
        assertEquals("test-family-with-simple-font", simpleFamily!!.name)

        val simpleFontList = simpleFamily.fonts[Fonts.Weight.NORMAL]
        assertNotNull(simpleFontList)
        assertEquals(1, simpleFontList!!.size)

        val simpleFont = simpleFontList.first()
        assertEquals("SimpleFont.ttf", simpleFont.name)

        assertEquals(2, families.size)
        assertTrue(families.containsKey("SECCJK-Regular.ttc"))
        assertTrue(families.containsKey("test-family-with-simple-font"))

        val cjkFamily = families["SECCJK-Regular.ttc"]
        assertNotNull(cjkFamily)
        assertEquals("", cjkFamily!!.name) // Unnamed family
        assertEquals("zh-Hans", cjkFamily.lang)

        val cjkNormalFonts = cjkFamily.fonts[Fonts.Weight.NORMAL]
        assertNotNull(cjkNormalFonts)
        assertEquals(1, cjkNormalFonts!!.size)
        val cjkBoldFonts = cjkFamily.fonts[Fonts.Weight.BOLD]
        assertNotNull(cjkBoldFonts)
        assertEquals(1, cjkBoldFonts!!.size)

        val cjkFirstFont = cjkNormalFonts.first { it.name == "SECCJK-Regular.ttc" }
        assertNotNull(cjkFirstFont)
        assertEquals(400, cjkFirstFont.weight.weight)
        assertEquals(Fonts.Font.STYLE_NORMAL, cjkFirstFont.style)
        assertEquals(2, cjkFirstFont.ttcIndex)
        assertEquals("SECCJKjp-Regular", cjkFirstFont.postScriptName)
        assertNull(cjkFirstFont.fallbackFor)

        val cjkSecondFont = cjkBoldFonts.first { it.name == "NotoSerifCJK-Bold.ttc" }
        assertNotNull(cjkSecondFont)
        assertEquals(700, cjkSecondFont.weight.weight)
        assertEquals(Fonts.Font.STYLE_NORMAL, cjkSecondFont.style)
        assertEquals(3, cjkSecondFont.ttcIndex)
        assertEquals("NotoSerifCJKjp-Bold", cjkSecondFont.postScriptName)
        assertEquals("serif", cjkSecondFont.fallbackFor)
    }

    @Test
    fun parseFontsToListBasic() {
        context.resources.openRawResource(R.raw.fonts).use { fontStream ->
            val systemFamiliesList = SystemFontsParser.parseFontsXML(fontStream)
            assertTrue(systemFamiliesList.isNotEmpty())

            val sansSerifFamily = systemFamiliesList.find { it.name == "sans-serif" }
            assertNotNull(sansSerifFamily)
            assertTrue(sansSerifFamily!!.fonts.isNotEmpty())

            val robotoRegular =
                sansSerifFamily.fonts[Fonts.Weight.NORMAL]?.find { it.name == "Roboto-Regular.ttf" }
            assertNotNull(robotoRegular)
        }
    }

    @Test
    fun getSystemFontListBasic() {
        // Clean slate...
        FontHelper.resetForTesting()
        val familiesList = FontHelper.getSystemFontList()
        assertTrue(familiesList.isNotEmpty())
        assertTrue(familiesList.all { it.fonts.isNotEmpty() })
    }

    @Test
    fun unnamedFontsDetectionList() {
        val xmlWithFallbacks = """
    <?xml version="1.0" encoding="utf-8"?>
    <familyset>
        <family name="sans-serif">
            <font weight="400">Roboto-Regular.ttf</font>
        </family>
        <family lang="und-Arab"> <!-- Unnamed, lang specified -->
            <font weight="400">NotoNaskhArabic-Regular.ttf</font>
        </family>
        <family> <!-- Unnamed, no lang -->
            <font weight="400">DroidSansFallback.ttf</font>
        </family>
    </familyset>
    """.trimIndent()

        val inputStream = ByteArrayInputStream(xmlWithFallbacks.toByteArray(Charsets.UTF_8))
        val familiesList = SystemFontsParser.parseFontsXML(inputStream)

        assertEquals(3, familiesList.size)

        val sansSerif = familiesList.find { it.name == "sans-serif" }
        assertNotNull(sansSerif)

        val arabicFamily =
            familiesList.find { family -> family.name.isNullOrEmpty() && family.lang == "und-Arab" }
        assertNotNull(arabicFamily)
        assertEquals(
            "NotoNaskhArabic-Regular.ttf",
            arabicFamily!!.fonts.values.flatten().first().name
        )

        val droidFallbackFamily =
            familiesList.find { family -> family.name.isNullOrEmpty() && family.lang == null }
        assertNotNull(droidFallbackFamily)
        assertEquals(
            "DroidSansFallback.ttf",
            droidFallbackFamily!!.fonts.values.flatten().first().name
        )
    }

    @Test
    fun testParseDuplicateFamilyNamesList() {
        // The list should contain both definitions if families have the same name
        val xml = """
             <?xml version="1.0" encoding="utf-8"?>
             <familyset>
                 <family name="duplicate">
                     <font weight="400">FirstFont.ttf</font>
                 </family>
                 <family name="other">
                     <font weight="400">OtherFont.ttf</font>
                 </family>
                 <family name="duplicate"> <!-- Second definition -->
                     <font weight="400">SecondFont.ttf</font>
                 </family>
             </familyset>
         """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        // parseFontsXML includes alias resolution which might affect the final list structure
        // if these were aliases. Here they are direct families.
        val resultList = SystemFontsParser.parseFontsXML(inputStream)

        assertEquals(3, resultList.size)

        val duplicateFamilies = resultList.filter { it.name == "duplicate" }
        assertEquals(2, duplicateFamilies.size)

        // Check that both versions are present
        assertTrue(
            duplicateFamilies.any { it.fonts[Fonts.Weight.NORMAL]?.firstOrNull()?.name == "FirstFont.ttf" })
        assertTrue(
            duplicateFamilies.any { it.fonts[Fonts.Weight.NORMAL]?.firstOrNull()?.name == "SecondFont.ttf" })

        val otherFamily = resultList.find { it.name == "other" }
        assertNotNull(otherFamily)
    }

    @Test
    fun testParseRecursiveAliasesList() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <familyset>
                <family name="actual-font-family">
                    <font weight="400">ActualFont.ttf</font>
                </family>
                <alias name="alias-b" to="actual-font-family" />
                <alias name="alias-a" to="alias-b" />
            </familyset>
        """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val resultList = SystemFontsParser.parseFontsXML(inputStream)

        assertEquals(3, resultList.size)

        val actualFamily = resultList.find { it.name == "actual-font-family" }
        assertNotNull(actualFamily)

        val aliasB = resultList.find { it.name == "alias-b" }
        assertNotNull(aliasB)
        assertEquals(actualFamily!!.fonts, aliasB!!.fonts)

        val aliasA = resultList.find { it.name == "alias-a" }
        assertNotNull(aliasA)
        assertEquals(actualFamily.fonts, aliasA!!.fonts)
    }

    @Test
    fun testParseAliasNameCollisionList() {
        // If a family and an alias have the same name, the family should take precedence,
        // and the alias should not be added.
        val xml = """
             <?xml version="1.0" encoding="utf-8"?>
             <familyset>
                 <family name="collision-name">
                     <font weight="400">FamilyFont.ttf</font>
                 </family>
                 <family name="another-family">
                     <font weight="400">AnotherFont.ttf</font>
                 </family>
                 <alias name="collision-name" to="another-family" />
             </familyset>
         """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val resultList = SystemFontsParser.parseFontsXML(inputStream)

        // The alias 'collision-name' should not be created because a family with that name exists.
        assertEquals(2, resultList.size)

        val collisionFamily = resultList.find { it.name == "collision-name" }
        assertNotNull(collisionFamily)
        assertEquals(
            "FamilyFont.ttf",
            collisionFamily!!.fonts[Fonts.Weight.NORMAL]?.firstOrNull()?.name
        )

        val anotherFamily = resultList.find { it.name == "another-family" }
        assertNotNull(anotherFamily)
    }

    @Test
    fun testParseNestedFamilysetList() {
        val xml = """
             <?xml version="1.0" encoding="utf-8"?>
             <familyset> <!-- Root familyset -->
                 <family name="outer-family">
                     <font weight="400">OuterFont.ttf</font>
                 </family>
                 <familyset> <!-- Nested familyset -->
                     <family name="inner-family">
                         <font weight="400">InnerFont.ttf</font>
                     </family>
                     <alias name="inner-alias" to="inner-family"/>
                 </familyset>
                 <alias name="outer-alias" to="outer-family"/>
             </familyset>
         """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val resultList =
            SystemFontsParser.parseFontsXML(inputStream) // Calls readRootElement (list version)

        // Expected: outer-family, inner-family, resolved outer-alias, resolved inner-alias
        assertEquals(4, resultList.size)

        val outerFamily = resultList.find { it.name == "outer-family" }
        assertNotNull(outerFamily)
        val innerFamily = resultList.find { it.name == "inner-family" }
        assertNotNull(innerFamily)
        val outerAlias = resultList.find { it.name == "outer-alias" }
        assertNotNull(outerAlias)
        val innerAlias = resultList.find { it.name == "inner-alias" }
        assertNotNull(innerAlias)

        assertEquals(outerFamily!!.fonts, outerAlias!!.fonts)
        assertEquals(innerFamily!!.fonts, innerAlias!!.fonts)
    }

    @Test
    fun testOrderOfFamiliesAndAliasesInList() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <familyset>
                <alias name="alias-c" to="family-c" /> <!-- Target defined later -->
                <family name="family-a">
                    <font weight="400">FontA.ttf</font>
                </family>
                <family name="family-b">
                    <font weight="400">FontB.ttf</font>
                </family>
                <alias name="alias-a" to="family-a" />
                <family name="family-c">
                    <font weight="400">FontC.ttf</font>
                </family>
            </familyset>
        """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
        val resultList = SystemFontsParser.parseFontsXML(inputStream)

        // Expected: family-a, family-b, family-c (from parsing)
        // then alias-a (resolves to family-a), alias-c (resolves to family-c) are added.
        // The exact order of resolved aliases at the end might depend on the iteration,
        // but original families should generally appear before resolved aliases.
        assertEquals(5, resultList.size)

        val namesInOrder = resultList.mapNotNull { it.name }

        val familyAIndex = namesInOrder.indexOf("family-a")
        val familyBIndex = namesInOrder.indexOf("family-b")
        val familyCIndex = namesInOrder.indexOf("family-c")
        val aliasAIndex = namesInOrder.indexOf("alias-a")
        val aliasCIndex = namesInOrder.indexOf("alias-c")

        assertTrue(familyAIndex != -1)
        assertTrue(familyBIndex != -1)
        assertTrue(familyCIndex != -1)
        assertTrue(aliasAIndex != -1)
        assertTrue(aliasCIndex != -1)

        // Check relative order of original families
        assertTrue(familyAIndex < familyBIndex)
        assertTrue(familyBIndex < familyCIndex)

        // Check that resolved aliases appear after the original families they might depend on
        // (or generally towards the end of the list as they are appended during resolution).
        assertTrue(aliasAIndex > familyAIndex)
        assertTrue(aliasCIndex > familyCIndex)
    }

    @Test
    fun testConcurrentSystemFontListAccess() {
        // Clean slate...
        FontHelper.resetForTesting()

        val numThreads = 10
        val threads = List(numThreads) {
            Thread {
                val fontList = FontHelper.getSystemFontList()
                assertFalse(fontList.isEmpty())
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val finalList = FontHelper.getSystemFontList()
        assertFalse(finalList.isEmpty())
    }

    @Test
    fun testMultipleUnnamedFamiliesWithDifferentLanguages() {
        // Test that the List approach correctly handles multiple unnamed families
        // with different languages but potentially same font names
        val xmlWithMultipleUnnamed = """
        <?xml version="1.0" encoding="utf-8"?>
        <familyset>
            <family name="sans-serif">
                <font weight="400">Roboto-Regular.ttf</font>
            </family>
            <family lang="ko">
                <font weight="400">Default.ttf</font>
            </family>
            <family lang="ja">
                <font weight="400">Default.ttf</font>
            </family>
            <family lang="zh-Hans">
                <font weight="400">Default.ttf</font>
            </family>
        </familyset>
    """.trimIndent()

        val inputStream = ByteArrayInputStream(xmlWithMultipleUnnamed.toByteArray(Charsets.UTF_8))
        val families = SystemFontsParser.parseFontsXML(inputStream)

        assertEquals("Should have 4 families total", 4, families.size)

        val unnamedFamilies = families.filter { it.name.isNullOrEmpty() }
        assertEquals("Should have 3 unnamed families", 3, unnamedFamilies.size)

        // Verify all languages are preserved
        val languages = unnamedFamilies.map { it.lang }.toSet()
        assertEquals(setOf("ko", "ja", "zh-Hans"), languages)
    }

    @Test
    fun testCollidingUnnamedFamilies() {
        val xmlWithCollidingUnnamedFamilies = """
        <?xml version="1.0" encoding="utf-8"?>
        <familyset>
            <!-- Named family - should work fine in both approaches -->
            <family name="sans-serif">
                <font weight="400">Roboto-Regular.ttf</font>
                <font weight="700">Roboto-Bold.ttf</font>
            </family>
            
            <!-- First unnamed family for Korean -->
            <family lang="ko">
                <font weight="400">CommonFont.ttf</font>
                <font weight="700">KoreanSpecific-Bold.ttf</font>
            </family>
            
            <!-- Second unnamed family for Japanese - COLLISION! Same first font name -->
            <family lang="ja">
                <font weight="400">CommonFont.ttf</font>
                <font weight="700">JapaneseSpecific-Bold.ttf</font>
            </family>
            
            <!-- Third unnamed family for Chinese - ANOTHER COLLISION! -->
            <family lang="zh-Hans">
                <font weight="400">CommonFont.ttf</font>
                <font weight="700">ChineseSpecific-Bold.ttf</font>
            </family>
        </familyset>
    """.trimIndent()

        // Map loses data with collisions...
        val mapStream =
            ByteArrayInputStream(xmlWithCollidingUnnamedFamilies.toByteArray(Charsets.UTF_8))
        val mapResult = SystemFontsParser.parseFontsXMLMap(mapStream)

        // Preserving all data with lists
        val listStream =
            ByteArrayInputStream(xmlWithCollidingUnnamedFamilies.toByteArray(Charsets.UTF_8))
        val listResult = SystemFontsParser.parseFontsXML(listStream)

        assertEquals(2, mapResult.size)

        // List approach preserves all data - all 4 families
        assertEquals(4, listResult.size)

        assertTrue(mapResult.containsKey("sans-serif"))
        assertNotNull(listResult.find { it.name == "sans-serif" })

        val mapUnnamedFamilies = mapResult.values.filter { it.name.isNullOrEmpty() }
        assertEquals(1, mapUnnamedFamilies.size)

        val listUnnamedFamilies = listResult.filter { it.name.isNullOrEmpty() }
        assertEquals(3, listUnnamedFamilies.size)

        // In the map approach, we lose the Korean and Japanese families
        // Only the Chinese family survives (since it's processed last)
        val mapSurvivorFamily = mapUnnamedFamilies.first()
        assertEquals("zh-Hans", mapSurvivorFamily.lang)
        assertEquals(
            "ChineseSpecific-Bold.ttf",
            mapSurvivorFamily.fonts[Fonts.Weight.BOLD]?.first()?.name
        )

        // In the list approach, we have all three language-specific families
        val listLanguages = listUnnamedFamilies.map { it.lang }.toSet()
        assertEquals(setOf("ko", "ja", "zh-Hans"), listLanguages)

        // These have bold fonts
        val koFamily = listUnnamedFamilies.find { it.lang == "ko" }
        val jaFamily = listUnnamedFamilies.find { it.lang == "ja" }
        val zhFamily = listUnnamedFamilies.find { it.lang == "zh-Hans" }

        assertNotNull(koFamily)
        assertNotNull(jaFamily)
        assertNotNull(zhFamily)

        assertEquals(
            "KoreanSpecific-Bold.ttf",
            koFamily!!.fonts[Fonts.Weight.BOLD]?.first()?.name
        )
        assertEquals(
            "JapaneseSpecific-Bold.ttf",
            jaFamily!!.fonts[Fonts.Weight.BOLD]?.first()?.name
        )
        assertEquals(
            "ChineseSpecific-Bold.ttf",
            zhFamily!!.fonts[Fonts.Weight.BOLD]?.first()?.name
        )
    }
}
