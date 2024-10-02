package app.rive.runtime.kotlin.fonts

import android.util.Log
import android.util.Xml
import androidx.annotation.VisibleForTesting
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream


class Fonts {
    data class Font(
        val weight: Weight,
        val style: String,
        val name: String,  // TTF file name
        val axis: List<Axis>? = null,  // Optional axis for variable fonts
    ) {
        companion object {
            const val STYLE_NORMAL = "normal"
            const val STYLE_ITALIC = "italic"

            val DEFAULT = Font(
                weight = Weight.NORMAL,
                style = STYLE_NORMAL,
                name = "Roboto-Regular.ttf"
            )

        }
    }

    data class Axis(
        val tag: String,
        val styleValue: String,
    )

    data class Family(
        val name: String? = null, // Optional for unnamed family groups
        val variant: String? = null,
        val lang: String? = null, // Optional language specification
        val fonts: Map<Weight, List<Font>>,
    )

    data class Alias(
        val name: String,
        val original: String,
        val weight: Weight? = Weight.NORMAL,
    )

    data class FamilySet(
        val version: String,
        val families: List<Family>,
        val aliases: List<Alias>,
    )

    data class Weight(val weight: Int = 400) {
        companion object {
            fun fromString(stringValue: String?): Weight = Weight(
                stringValue?.toIntOrNull()?.coerceIn(0..1000) ?: 400
            )

            fun fromInt(intValue: Int = 400): Weight = Weight(
                intValue.coerceIn(0..1000)
            )

            val NORMAL = Weight(weight = 400)
            val BOLD = Weight(weight = 700)
        }
    }

    data class FontOpts(
        val familyName: String? = null,
        val lang: String? = null, // TODO: maybe this should be an enum?
        val weight: Weight = Weight.NORMAL,
        val style: String = Font.STYLE_NORMAL,
    ) {
        companion object {
            /**
             * Default configuration for font options, intended for general usage.
             *
             * This field provides a default set of options with a widely supported
             * family name of "sans-serif", default weight as [Weight.NORMAL] (equivalent to
             * 400), and style as [Font.STYLE_NORMAL]. This setup ensures that if no specific
             * preferences are specified, a broadly compatible and visually neutral
             * font configuration is used.
             *
             * This can be particularly useful in scenarios where specific font attributes
             * are not critical, or where a robust and fail-safe font selection is needed.
             */
            val DEFAULT = FontOpts(familyName = "sans-serif")
        }
    }

    data class FileFont(
        val name: String,
        val variant: String? = null,
        val lang: String? = null,
    )
}

class FontHelper {
    companion object {
        private const val TAG = "FontHelper"

        /**
         * Retrieves a fallback font based on optional font preferences.
         *
         * This function searches through the system's available fonts and selects a font
         * that matches the provided options. If no options are provided, it uses the
         * default settings (v. [Fonts.FontOpts.DEFAULT]
         *
         * @param opts Optional [Fonts.FontOpts] specifying the desired font
         *             characteristics. If not provided, defaults are used.
         * @return The [Fonts.Font] matching the specified options, or `null` if no
         *         suitable font is found.
         */
        fun getFallbackFont(opts: Fonts.FontOpts? = null): Fonts.Font? {
            val fontFamilies = getSystemFonts()
            if (fontFamilies.isEmpty()) {
                Log.e(TAG, "getFallbackFont: no system font found")
                return null
            }
            val match = findMatch(fontFamilies, opts) ?: return null
            // Return match only if it exists on the File System.
            return getFontFile(match)?.let { match }
        }

        internal fun findMatch(
            fontFamilies: Map<String, Fonts.Family>,
            opts: Fonts.FontOpts? = null,
        ): Fonts.Font? {
            val fontOpts = opts ?: Fonts.FontOpts.DEFAULT
            val (
                familyName,
                lang,
                weight,
                style,
            ) = fontOpts

            val matchingFamilies = fontFamilies
                .filter { (key, value) ->
                    (familyName == null || key == familyName) &&
                            (lang == null || value.lang == lang)
                }
                .values
                .sortedByDescending { it.lang == lang }

            val fontCandidate = matchingFamilies
                .flatMap { it.fonts[weight] ?: emptyList() }
                .firstOrNull { it.style == style }

            return fontCandidate ?: run {
                Log.w(TAG, "getFallbackFont(): failed to find a matching for for $fontOpts")
                null
            }
        }

        /**
         * Retrieves the file for a specified font from the system's font paths.
         *
         * Searches through predefined system font paths to find the file corresponding
         * to the specified font.
         *
         * @param font The [Fonts.Font] object representing the font whose file is being
         *             sought.
         * @return A [File] object pointing to the font file, or `null` if the file does
         *         not exist.
         */
        fun getFontFile(font: Fonts.Font): File? = SystemFontsParser.SYSTEM_FONTS_PATHS
            .asSequence()
            .map { basePath -> File(basePath, font.name.trim()) }
            .firstOrNull { it.exists() }

        /**
         * Reads and returns the bytes of a font file for a specified font.
         *
         * This function uses [getFontFile] to find the font file and reads its bytes
         * into a ByteArray.
         *
         * @param font The [Fonts.Font] object representing the font to be read.
         * @return A [ByteArray] containing the font file's bytes, or `null` if the file
         *         could not be found or read.
         */
        fun getFontBytes(font: Fonts.Font): ByteArray? = getFontFile(font)
            ?.readBytes()

        /**
         * Retrieves a map of all system fonts available.
         *
         * This function attempts to find a valid XML configuration file from a set of
         * possible paths and parses it to a map of font families.
         *
         * @return A [Map]<String, Fonts.Family> representing all the available font
         *         families, or an empty map if no valid XML is found.
         */
        fun getSystemFonts(): Map<String, Fonts.Family> {
            val validPath = sequenceOf(
                SystemFontsParser.FONTS_XML_PATH,
                SystemFontsParser.SYSTEM_FONTS_XML_PATH,
                SystemFontsParser.FALLBACK_FONTS_XML_PATH
            )
                .map { pathStr -> File(pathStr) }
                .firstOrNull() { it.exists() }
            return validPath?.inputStream()?.use {
                // Make sure it returns fonts that exist on the File System.
                filterNonExistingFonts(
                    SystemFontsParser.parseFontsXML(it)
                )
            }
                ?: emptyMap()
        }

        private fun filterNonExistingFonts(
            fontFamilies: Map<String, Fonts.Family>,
        ): Map<String, Fonts.Family> {
            val filtered = mutableMapOf<String, Fonts.Family>()
            fontFamilies.forEach { (familyName, family) ->
                val existingFonts = family
                    .fonts.mapValues { (_, fontList) ->
                        fontList.filter { font -> getFontFile(font) != null }
                    }.filterValues { it.isNotEmpty() } // Keep only non-empty lists.

                if (existingFonts.isNotEmpty()) {
                    filtered[familyName] = Fonts.Family(
                        familyName,
                        family.variant,
                        family.lang,
                        existingFonts,
                    )
                }
            }

            return filtered
        }

        /**
         * Retrieves the byte array of a fallback font specified by optional font options.
         *
         * This function combines the functionality of `getFallbackFont` and `getFontBytes`
         * to fetch the bytes of a font that matches the provided options. If no specific
         * options are provided, default settings are used. This is useful when direct
         * byte-level access to a font file is required, such as for sending font data
         * over a network or loading it into a custom rendering system.
         *
         * @param opts Optional [Fonts.FontOpts] specifying the desired font
         *             characteristics. If not provided, default options are used.
         * @return A [ByteArray] containing the font's data, or `null` if no suitable
         *         font is found or if there is an error accessing the font file.
         */
        fun getFallbackFontBytes(opts: Fonts.FontOpts? = null): ByteArray? =
            getFallbackFont(opts)?.let {
                return getFontBytes(it)
            }
    }
}

class SystemFontsParser {
    companion object {
        private const val TAG = "SystemFontsParser"
        internal const val FONTS_XML_PATH = "/system/etc/fonts.xml"
        internal const val SYSTEM_FONTS_XML_PATH = "/system/etc/system_fonts.xml"
        internal const val FALLBACK_FONTS_XML_PATH = "/system/etc/system_fallback.xml"

        internal val SYSTEM_FONTS_PATHS = listOf(
            "/system/fonts/",
            "/system/font/",
            "/data/fonts/",
            "/system/product/fonts/",
        )

        internal fun parseFontsXML(xmlFileStream: InputStream): Map<String, Fonts.Family> {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(xmlFileStream, null)
                nextTag()
            }

            return readXML(parser)
        }

        private fun readXML(parser: XmlPullParser): Map<String, Fonts.Family> {
            val familiesMap = mutableMapOf<String, Fonts.Family>()
            parser.require(XmlPullParser.START_TAG, null, "familyset")

            while (keepReading(parser)) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                val tag = parser.name.trim()
                when (tag) {
                    "family" -> {
                        parser.getAttributeValue(null, "name")?.let { name ->
                            readFamily(name.trim(), parser)?.let {
                                familiesMap[name] = it
                            }
                        } ?: run {
                            // null name - possibly a legacy family?
                            readLegacyFamily(parser)?.let { (family, aliases) ->
                                val familyName = family.name!!.trim()
                                familiesMap[familyName] = family
                                aliases.forEach { alias ->
                                    remapAlias(alias, familiesMap)?.let { remapped ->
                                        familiesMap[alias.name] = remapped
                                    }
                                }
                            }
                        }
                    }

                    "alias" -> {
                        val alias = readAlias(parser)
                        remapAlias(alias, familiesMap)?.let {
                            familiesMap[alias.name] = it
                        }
                    }

                    else -> skip(parser)
                }
            }

            return familiesMap
        }

        private fun remapAlias(
            alias: Fonts.Alias,
            families: Map<String, Fonts.Family>,
        ): Fonts.Family? {
            val familyName = alias.original
            val ogFamily = families[familyName] ?: return null

            val weight = alias.weight ?: return ogFamily
            val weightedFonts = ogFamily.fonts[weight] ?: return ogFamily

            val (name, variant, lang) = ogFamily

            return Fonts.Family(
                name,
                variant,
                lang,
                mapOf(weight to weightedFonts),
            )
        }

        private fun readFamily(familyName: String, parser: XmlPullParser): Fonts.Family? {
            val lang = parser.getAttributeValue(null, "lang")
            val variant = parser.getAttributeValue(null, "variant")
            val ignore = parser.getAttributeValue(null, "ignore")
            // Certain families support multiple fonts with the same weight but different styles
            // e.g. "normal" vs "italic"
            val fonts = mutableMapOf<Fonts.Weight, MutableList<Fonts.Font>>()

            while (keepReading(parser)) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                val tag = parser.name.trim()
                when (tag) {
                    "font" -> {
                        val font = readFont(parser)
                        if (!fonts.contains(font.weight)) {
                            fonts[font.weight] = mutableListOf()
                        }
                        fonts[font.weight]?.add(font)

                    }

                    else -> skip(parser)
                }
            }
            val skip = (ignore != null && (ignore == "true" || ignore == "1"))
            if (skip || fonts.isEmpty()) {
                return null
            }

            return Fonts.Family(
                name = familyName,
                variant = variant,
                lang = lang,
                fonts = fonts,
            )
        }

        private fun readLegacyFamily(parser: XmlPullParser): Pair<Fonts.Family, List<Fonts.Alias>>? {
            val namesList = mutableListOf<String>()
            val filesList = mutableListOf<Fonts.FileFont>()
            val fontList = mutableListOf<Fonts.Font>()
            val familyVariant = parser.getAttributeValue(null, "variant")
            val familyLang = parser.getAttributeValue(null, "lang")

            while (keepReading(parser)) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                val tag = parser.name.trim()
                when (tag) {
                    "fileset" -> {
                        filesList.addAll(
                            readFileset(parser)
                        )
                    }

                    "nameset" -> {
                        namesList.addAll(
                            readNameset(parser)
                        )
                    }

                    "font" -> {
                        val font = readFont(parser)
                        fontList.add(font)
                    }

                    else -> skip(parser)
                }
            }

            if (fontList.isNotEmpty()) {
                return fromFontList(fontList, lang = familyLang, variant = familyVariant)
            }

            if (filesList.isEmpty()) return null
            val familyName =
                if (namesList.isEmpty()) filesList.first().name else namesList.removeFirst()
            if (familyName.isEmpty()) return null

            return fromFileFonts(
                filesList,
                namesList,
                familyName
            )
        }


        /**
         * The font files are listed in the order of the styles which they
         *     support: regular, bold, italic and bold-italic.
         */
        private val fontFilesOrder = listOf(
            Pair(Fonts.Weight.NORMAL, Fonts.Font.STYLE_NORMAL),
            Pair(Fonts.Weight.BOLD, Fonts.Font.STYLE_NORMAL),
            Pair(Fonts.Weight.NORMAL, Fonts.Font.STYLE_ITALIC),
            Pair(Fonts.Weight.BOLD, Fonts.Font.STYLE_ITALIC),
        )

        private fun fromFileFonts(
            filesList: List<Fonts.FileFont>,
            namesList: List<String>,
            familyName: String,
        ): Pair<Fonts.Family, List<Fonts.Alias>> {
            val fontsMap: MutableMap<Fonts.Weight, MutableList<Fonts.Font>> = mutableMapOf()

            filesList.forEachIndexed { index, filefont ->
                val (weight, style) = fontFilesOrder[index]
                val (filename) = filefont
                val candidate = Fonts.Font(weight, style, filename)

                val fontsList = fontsMap.getOrPut(weight) { mutableListOf() }
                fontsList.add(candidate)
            }

            val aliases = namesList.map {
                Fonts.Alias(name = it, original = familyName)
            }

            val (_, variant, lang) = filesList.first()

            return Pair(
                Fonts.Family(
                    name = familyName,
                    fonts = fontsMap,
                    variant = variant,
                    lang = lang,
                ),
                aliases,
            )
        }

        private fun fromFontList(
            fontList: List<Fonts.Font>,
            lang: String?,
            variant: String?,
        ): Pair<Fonts.Family, List<Fonts.Alias>> {
            val fontsMap: MutableMap<Fonts.Weight, MutableList<Fonts.Font>> = mutableMapOf()

            fontList
                .forEach { font ->
                    val (weight, style, fontName) = font
                    fontsMap
                        .getOrPut(weight) { mutableListOf() }
                        .add(font)
                }

            val familyName = fontList.first().name

            return Pair(
                Fonts.Family(
                    name = familyName,
                    fonts = fontsMap,
                    variant = variant,
                    lang = lang,
                ),
                emptyList(), // not really a thing here...
            )
        }

        private fun readFont(parser: XmlPullParser): Fonts.Font {
            parser.require(XmlPullParser.START_TAG, null, "font")
            val weightStr = parser.getAttributeValue(null, "weight")
            val weight = Fonts.Weight.fromString(weightStr)
            val style = parser.getAttributeValue(null, "style")

            val filenameBuilder = StringBuilder()
            val axes = mutableListOf<Fonts.Axis>()

            while (keepReading(parser)) {
                if (parser.eventType == XmlPullParser.TEXT) {
                    filenameBuilder.append(parser.text.trim())
                }
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }


                val tag = parser.name.trim()
                if (tag == "axis") {
                    axes.add(readAxis(parser))
                } else {
                    skip(parser)
                }
            }

            return Fonts.Font(weight, style, filenameBuilder.toString())
        }

        private fun readText(parser: XmlPullParser): String {
            return if (parser.next() == XmlPullParser.TEXT) {
                val result = parser.text.trim()
                parser.nextTag()
                return result
            } else ""
        }

        private fun readNameset(parser: XmlPullParser): List<String> {
            val namesetList = mutableListOf<String>()
            while (keepReading(parser)) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                val tag = parser.name.trim()
                if (tag != "name") continue

                val nameset = readText(parser)
                if (nameset.isNotEmpty()) namesetList.add(nameset)
            }

            return namesetList
        }

        private fun readFileset(parser: XmlPullParser): List<Fonts.FileFont> {
            val filesetList = mutableListOf<Fonts.FileFont>()
            while (keepReading(parser)) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                val tag = parser.name.trim()
                if (tag != "file") continue

                val variant = parser.getAttributeValue(null, "variant")
                val lang = parser.getAttributeValue(null, "lang")

                val filesetName = readText(parser)
                if (filesetName.isNotEmpty()) {
                    filesetList.add(
                        Fonts.FileFont(filesetName, variant, lang)
                    )
                }
            }

            return filesetList
        }

        private fun readAxis(parser: XmlPullParser): Fonts.Axis {
            val tagStr = parser.getAttributeValue(null, "tag")
            val styleValueStr = parser.getAttributeValue(null, "stylevalue")
            skip(parser) // ignore empty axis tag
            return Fonts.Axis(tagStr, styleValueStr)
        }

        private fun readAlias(parser: XmlPullParser): Fonts.Alias {
            val name = parser.getAttributeValue(null, "name")
            val to = parser.getAttributeValue(null, "to")
            val weight: Fonts.Weight? = parser
                .getAttributeValue(null, "weight")
                ?.let { weightStr ->
                    Fonts.Weight.fromString(weightStr)
                }

            skip(parser) // move past empty tag.

            return Fonts.Alias(name.trim(), to, weight)
        }


        private fun skip(parser: XmlPullParser) {
            var depth = 1
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.END_DOCUMENT -> return
                }
            }
        }

        private fun keepReading(parser: XmlPullParser): Boolean {
            val next = parser.next()
            return (next != XmlPullParser.END_TAG) && next != XmlPullParser.END_DOCUMENT
        }
    }
}

object NativeFontHelper {
    @VisibleForTesting
    external fun cppGetSystemFontBytes(): ByteArray

    @VisibleForTesting
    external fun cppHasGlyph(queryString: String): Boolean

    external fun cppRegisterFallbackFont(fontBytes: ByteArray): Boolean
}