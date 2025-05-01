package app.rive.runtime.kotlin.fonts

import android.util.Log
import android.util.Xml
import androidx.annotation.VisibleForTesting
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

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

    data class Weight(val weight: Int = 400) : Comparable<Weight> {
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

        override fun compareTo(other: Weight): Int = this.weight.compareTo(other.weight)
    }

    data class FontOpts(
        val familyName: String? = null,
        val lang: String? = null,
        val weight: Weight? = Weight.NORMAL,
        val style: String? = Font.STYLE_NORMAL,
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

        // Thread-safe atomic reference for the font cache
        private val fontCache = AtomicReference<Map<String, Fonts.Family>>(null)

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
            // Return cached fonts if available
            fontCache.get()?.let { return it }

            return synchronized(this) {
                fontCache.get() ?: loadFonts()
            }
        }

        /**
         * Loads and parses the system font configuration from the first available XML file found
         * at predefined system paths, filters the results based on font file existence, and
         * updates the internal font cache.
         *
         * This function attempts to locate a font configuration file by checking the following
         * paths in order:
         * 1. [SystemFontsParser.FONTS_XML_PATH] (`/system/etc/fonts.xml`)
         * 2. [SystemFontsParser.SYSTEM_FONTS_XML_PATH] (`/system/etc/system_fonts.xml`)
         * 3. [SystemFontsParser.FALLBACK_FONTS_XML_PATH] (`/system/etc/system_fallback.xml`)
         *
         * It uses the first file that exists in this sequence.
         *
         * If a valid file is found, it is parsed using [SystemFontsParser.parseFontsXML].
         * If parsing fails or no configuration file is found at any of the expected paths,
         * an error or warning is logged, and an empty map is processed.
         *
         * The parsed font families are then filtered using `filterNonExistingFonts` to remove
         * any font entries whose corresponding `.ttf` or `.otf` file cannot be located on the
         * device's filesystem via [FontHelper.getFontFile].
         *
         * The resulting map of valid, existing font families is then stored in the internal
         * `fontCache` and returned.
         *
         * @return A [Map] of font family names to [Fonts.Family] objects, containing only
         *   fonts whose files were found on the filesystem. Returns an empty map if no
         *   configuration file was found, parsing failed, or no fonts passed the
         *   filesystem existence check.
         */
        internal fun loadFonts(): Map<String, Fonts.Family> {
            val validPath = sequenceOf(
                SystemFontsParser.FONTS_XML_PATH,
                SystemFontsParser.SYSTEM_FONTS_XML_PATH,
                SystemFontsParser.FALLBACK_FONTS_XML_PATH
            )
                .map { pathStr -> File(pathStr) }
                .firstOrNull { it.exists() }

            val loadedFonts = validPath?.inputStream()?.use { stream ->
                try {
                    SystemFontsParser.parseFontsXML(stream)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing fonts XML: ${e.message}", e)
                    emptyMap()
                }
            } ?: run {
                Log.w(TAG, "No valid system font XML file found at expected paths.")
                emptyMap()
            }

            // Filter out fonts that don't exist on the file system
            val existingFonts = filterNonExistingFonts(loadedFonts)

            // Update the cache atomically
            fontCache.set(existingFonts)

            return existingFonts
        }

        /**
         * Retrieves a fallback font based on optional font preferences.
         *
         * This function searches through the system's available fonts and selects a font
         * that matches the provided options. If no options are provided, it uses the
         * default settings (i.e. [Fonts.FontOpts.DEFAULT])
         *
         * @param opts Optional [Fonts.FontOpts] specifying the desired font
         *             characteristics. If not provided, defaults are used.
         * @return The [Fonts.Font] matching the specified options, or `null` if no
         *         suitable font is found.
         */
        fun getFallbackFont(opts: Fonts.FontOpts? = null): Fonts.Font? =
            getFallbackFonts(opts ?: Fonts.FontOpts.DEFAULT).firstOrNull()

        /**
         * Retrieves a list of fallback fonts based on optional font preferences.
         *
         * This function searches through the system's available fonts and returns a list
         * of fonts that match the provided options, ordered by priority with named fonts
         * first, followed by unnamed fallback fonts.
         *
         * @param opts Optional [Fonts.FontOpts] specifying the desired font
         *             characteristics. If not provided, defaults are used.
         * @return A [List] of [Fonts.Font] matching the specified options, or an empty list
         *         if no suitable fonts are found.
         */
        fun getFallbackFonts(opts: Fonts.FontOpts = Fonts.FontOpts.DEFAULT): List<Fonts.Font> {
            val fontFamilies = getSystemFonts()
            if (fontFamilies.isEmpty()) {
                Log.e(TAG, "getFallbackFonts: no system font found")
                return emptyList()
            }

            return findMatches(fontFamilies, opts)
        }

        /**
         * Helper function for [findMatches] to filter fonts based on weight and style.
         * Adds them to a result set to ensure uniqueness.
         */
        private fun filterFamilies(
            families: List<Fonts.Family>,
            resultSet: MutableSet<Fonts.Font>,
            weight: Fonts.Weight?,
            style: String?,
        ) {
            families.forEach { family ->
                val fontsForWeightSequence =
                    if (weight == null) {
                        family.fonts.values.asSequence().flatten()
                    } else {
                        family.fonts[weight]?.asSequence()
                            ?: emptySequence()
                    }

                fontsForWeightSequence.forEach { font ->
                    if (style.isNullOrBlank() || font.style == style) {
                        resultSet.add(font)
                    }
                }
            }
        }

        /**
         * Searches the provided map of font families for fonts matching the specified criteria,
         * returning an ordered list of unique matches.
         *
         * If the [opts] parameter is null, [Fonts.FontOpts.DEFAULT] will be used for matching.
         * Within the [opts], null properties (e.g., null `weight` or `style`) act as wildcards,
         * matching any value for that property. The `familyName` match is case-insensitive.
         *
         * The returned list prioritizes fonts as follows:
         * 1. Fonts from families explicitly named (not derived from filename).
         * 2. Fonts from unnamed (fallback) families.
         * Within these groups, families matching the requested language ([Fonts.FontOpts.lang])
         * are prioritized. The resulting list contains unique [Fonts.Font] instances.
         *
         * @param fontFamilies The map of font family names to [Fonts.Family] objects to search within.
         * @param opts The [Fonts.FontOpts] specifying the desired font characteristics (family,
         *   language, weight, style). Defaults to [Fonts.FontOpts.DEFAULT] if null.
         * @return A [List] of unique [Fonts.Font] objects matching the criteria, ordered by
         *   priority or an empty list if no matches are found.
         */
        internal fun findMatches(
            fontFamilies: Map<String, Fonts.Family>,
            opts: Fonts.FontOpts = Fonts.FontOpts.DEFAULT,
        ): List<Fonts.Font> {
            val familyName = opts.familyName
            val lang = opts.lang
            val weight = opts.weight
            val style = opts.style

            val matchingFamilies = fontFamilies
                .asSequence()
                .filter { (key, value) ->
                    (familyName == null || key.equals(familyName, ignoreCase = true)) &&
                            (lang == null || value.lang == lang)
                }
                .map { it.value }

            // = Separate named from unnamed families =
            val (namedFamilies, unnamedFamilies) =
                matchingFamilies.partition { family ->
                    // A family is considered "named" if it has a name that doesn't match any of its font filenames
                    val familyName = family.name
                    if (familyName.isNullOrBlank()) {
                        false
                    } else {
                        family.fonts.values.flatten().none { font -> familyName == font.name }
                    }
                }

            // Sort families to prioritize by language match using `sortedByDescending { it.lang == lang }`.
            // When no language is requested (`lang == null`), it makes sure that generic families
            // (i.e. `it.lang == null`) are processed before language-specific ones. When a
            // specific `lang` is requested, this sort won't affect the ordering of the
            // already-filtered lists.
            val sortedNamedFamilies = namedFamilies.sortedByDescending { it.lang == lang }
            val sortedUnnamedFamilies = unnamedFamilies.sortedByDescending { it.lang == lang }

            val resultFonts =
                LinkedHashSet<Fonts.Font>() // Preserve insertion order, ensures uniqueness
            filterFamilies(
                families = sortedNamedFamilies,
                weight = weight,
                style = style,
                resultSet = resultFonts
            )
            filterFamilies(
                families = sortedUnnamedFamilies,
                weight = weight,
                style = style,
                resultSet = resultFonts
            )

            // Combine the results with named fonts first
            return resultFonts.toList()
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

        private fun filterNonExistingFonts(
            fontFamilies: Map<String, Fonts.Family>,
        ): Map<String, Fonts.Family> {
            if (fontFamilies.isEmpty()) return fontFamilies

            val filtered = mutableMapOf<String, Fonts.Family>()
            fontFamilies.forEach { (familyName, family) ->
                val existingFonts = family
                    .fonts
                    .mapValues { (_, fontList) ->
                        fontList.filter { font -> getFontFile(font) != null }
                    }
                    .filterValues { it.isNotEmpty() } // Keep only non-empty lists.

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
            getFallbackFont(opts ?: Fonts.FontOpts.DEFAULT)?.let {
                return getFontBytes(it)
            }

        @VisibleForTesting
        fun resetForTesting() {
            fontCache.set(null)
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

            return readRootElement(parser)
        }

        private fun readRootElement(parser: XmlPullParser): Map<String, Fonts.Family> {
            parser.require(XmlPullParser.START_TAG, null, null) // Any start tag
            val rootTagName = parser.name

            // Check if it's a known root tag, otherwise log a warning but proceed
            if (rootTagName != "familyset" && rootTagName != "fonts-modification" && rootTagName != "config") {
                Log.w(TAG, "Unexpected root tag '$rootTagName' in font XML")
            }

            val familiesMap = mutableMapOf<String, Fonts.Family>()
            val aliases = mutableListOf<Fonts.Alias>()

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                when (parser.name.trim()) {
                    "family" -> readFamilyEntry(parser, aliases)?.let { family ->
                        familiesMap[family.name!!] = family
                    }

                    "alias" -> readAlias(parser)?.let { aliases.add(it) }
                    "familyset" -> {
                        readNestedFamilies(parser, familiesMap, aliases)
                    }

                    else -> skip(parser)
                }
            }

            aliases.forEach { alias ->
                // Only add alias if it doesn't overwrite an existing family AND the target exists
                if (!familiesMap.containsKey(alias.name)) {
                    remapAlias(alias, familiesMap)?.let { remappedFamily ->
                        familiesMap[alias.name] = remappedFamily
                    } ?: Log.w(
                        TAG,
                        "Could not remap alias '${alias.name}' because target '${alias.original}' not found."
                    )
                } else {
                    Log.w(
                        TAG,
                        "Skipping alias '${alias.name}' because a family with that name already exists."
                    )
                }
            }

            // Resolve nested aliases
            var unresolvedAliases = aliases.toMutableList()
            var progress = true
            while (progress && unresolvedAliases.isNotEmpty()) {
                progress = false
                val iterator = unresolvedAliases.iterator()
                while (iterator.hasNext()) {
                    val alias = iterator.next()
                    if (!familiesMap.containsKey(alias.name)) {
                        remapAlias(alias, familiesMap)?.let {
                            familiesMap[alias.name] = it
                            iterator.remove()
                            progress = true
                        }
                    } else {
                        iterator.remove()
                    }
                }
            }

            return familiesMap
        }

        private fun readNestedFamilies(
            parser: XmlPullParser,
            familiesMap: MutableMap<String, Fonts.Family>,
            aliases: MutableList<Fonts.Alias>,
        ) {
            parser.require(XmlPullParser.START_TAG, null, "familyset")
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name.trim()) {
                    "family" -> readFamilyEntry(parser, aliases)?.let { family ->
                        familiesMap[family.name!!] = family
                    }

                    "alias" -> readAlias(parser)?.let { aliases.add(it) }
                    else -> skip(parser)
                }
            }
        }

        private fun readFamilyEntry(
            parser: XmlPullParser,
            aliases: MutableList<Fonts.Alias>,
        ): Fonts.Family? {
            parser.require(XmlPullParser.START_TAG, null, "family")
            val familyNameAttr = getOptionalAttribute(parser, "name")?.trim()

            return if (familyNameAttr != null && familyNameAttr.isNotEmpty()) {
                // Modern family with a name attribute
                readFamily(familyNameAttr, parser)
            } else {
                // Legacy family (no name attribute) or potentially unnamed group
                readLegacyFamily(parser)?.let { (family, legacyAliases) ->
                    if (legacyAliases.isNotEmpty()) {
                        // This case is tricky. Where should these aliases go?
                        // The current structure adds them globally later.
                        // For now, we just return the family part.
                        // Consider if legacy aliases need special handling.
                        Log.w(
                            TAG,
                            "Legacy family generated aliases - these will be processed globally."
                        )
                        aliases.addAll(legacyAliases)
                    }
                    // Only return the family here.
                    family
                }
            }
        }

        private fun readAlias(parser: XmlPullParser): Fonts.Alias? {
            parser.require(XmlPullParser.START_TAG, null, "alias")
            return try {
                val name = getRequiredAttribute(parser, "name")
                val to = getRequiredAttribute(parser, "to")
                val weight: Fonts.Weight? = getOptionalAttribute(parser, "weight")
                    ?.let { weightStr -> Fonts.Weight.fromString(weightStr) }

                skip(parser)

                if (name.isBlank() || to.isBlank()) {
                    Log.w(
                        TAG,
                        "Skipping alias with blank name ('$name') or to ('$to')."
                    )
                    null
                } else {
                    Fonts.Alias(name.trim(), to.trim(), weight)
                }
            } catch (e: IllegalArgumentException) {
                Log.e(
                    TAG,
                    "Skipping alias due to missing required attribute: ${e.message}"
                )
                skip(parser) // Move past borked tag.
                null
            }
        }

        private fun remapAlias(
            alias: Fonts.Alias,
            families: Map<String, Fonts.Family>,
        ): Fonts.Family? {
            val familyName = alias.original
            val ogFamily = families[familyName] ?: return null

            val weight = alias.weight
            if (weight == null) {
                return Fonts.Family(
                    name = alias.name, // The alias name
                    variant = ogFamily.variant,
                    lang = ogFamily.lang,
                    fonts = ogFamily.fonts,
                )
            }

            val weightedFonts = ogFamily.fonts[weight]

            if (weightedFonts == null || weightedFonts.isEmpty()) {
                Log.w(
                    TAG,
                    "Alias '${alias.name}' targets weight ${weight.weight} in family '${alias.original}', but that doesn't exist"
                )
                return null // Failed to create an alias for a weight that doesn't exist
            }

            val (name, variant, lang) = ogFamily

            return Fonts.Family(
                name = alias.name,
                variant,
                lang,
                mapOf(weight to weightedFonts),
            )
        }

        private fun readFamily(familyName: String, parser: XmlPullParser): Fonts.Family? {
            val attributes = with(parser) {
                Triple(
                    getOptionalAttribute(this, "lang"),
                    getOptionalAttribute(this, "variant"),
                    getOptionalAttribute(this, "ignore")
                )
            }
            val (lang, variant, ignore) = attributes

            // Parse fonts into a weight->font map
            val fonts = mutableMapOf<Fonts.Weight, MutableList<Fonts.Font>>()

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                when (parser.name.trim()) {
                    "font" -> try {
                        readFont(parser).also { font ->
                            fonts.getOrPut(font.weight) { mutableListOf() }.add(font)
                        }
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Failed to read <font> in family '$familyName': ${e.message}",
                            e
                        )
                    }

                    else -> skip(parser)
                }
            }

            // Skip if flagged or empty
            if (ignore in listOf("true", "1") || fonts.isEmpty()) {
                return null
            }

            return Fonts.Family(
                name = familyName,
                variant = variant,
                lang = lang,
                fonts = fonts
            )
        }

        private fun readLegacyFamily(parser: XmlPullParser): Pair<Fonts.Family, List<Fonts.Alias>>? {
            val namesList = mutableListOf<String>()
            val filesList = mutableListOf<Fonts.FileFont>()
            val fontList = mutableListOf<Fonts.Font>()
            val familyVariant = getOptionalAttribute(parser, "variant")
            val familyLang = getOptionalAttribute(parser, "lang")

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                try {
                    when (parser.name.trim()) {
                        "fileset" -> {
                            filesList.addAll(readFileset(parser))
                        }

                        "nameset" -> {
                            namesList.addAll(readNameset(parser))
                        }

                        "font" -> {
                            val font = readFont(parser)
                            fontList.add(font)
                        }

                        else -> skip(parser)
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Error reading tag '${parser.name}' inside legacy family - Skipping tag - ${e.message}",
                        e
                    )
                }
            }

            if (fontList.isNotEmpty()) {
                val familyName = if (namesList.isNotEmpty()) namesList.removeAt(0) else {
                    val derivedFamilyName = fontList.firstOrNull()?.name?.trim()
                    if (derivedFamilyName.isNullOrBlank()) {
                        Log.e(
                            TAG,
                            "Could not derive family name from first font filename. Skipping family."
                        )
                        return null
                    }
                    derivedFamilyName
                }
                return fromFontList(
                    familyName,
                    fontList,
                    lang = familyLang,
                    variant = familyVariant
                )
            }

            if (filesList.isEmpty()) return null

            if (namesList.isEmpty()) {
                // No <nameset> was found, use the full name instead
                val derivedFamilyName = filesList.first().name.trim()
                if (derivedFamilyName.isBlank()) {
                    Log.e(
                        TAG,
                        "Legacy family's first file name '${filesList.first().name}' isn't a valid family name"
                    )
                    return null
                }
                namesList.add(derivedFamilyName)
            }

            val familyName = namesList.removeAt(0).trim()
            if (familyName.isEmpty()) {
                Log.e(TAG, "Legacy family's primary name from <nameset> is empty.")
                return null
            }

            return fromFileFonts(
                filesList,
                namesList,
                familyName = familyName,
                familyLang = familyLang,
                familyVariant = familyVariant,
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
            aliasNames: List<String>,
            familyName: String,
            familyLang: String?,
            familyVariant: String?,
        ): Pair<Fonts.Family, List<Fonts.Alias>> {
            val fontsMap: MutableMap<Fonts.Weight, MutableList<Fonts.Font>> = mutableMapOf()

            filesList.forEachIndexed { index, filefont ->
                if (index >= fontFilesOrder.size) {
                    Log.w(
                        TAG,
                        "Legacy family '$familyName' has more than ${fontFilesOrder.size} files in <fileset>. Ignoring extra file: '${filefont.name}'"
                    )
                    return@forEachIndexed // continue to next iteration
                }

                val (weight, style) = fontFilesOrder[index]
                val filename = filefont.name.trim()
                if (filename.isEmpty()) {
                    Log.w(
                        TAG,
                        "Skipping empty filename in <fileset> for family '$familyName'."
                    )
                    return@forEachIndexed
                }

                val font = Fonts.Font(weight = weight, style = style, name = filename, axis = null)

                fontsMap.getOrPut(weight) { mutableListOf() }.add(font)
            }

            if (fontsMap.isEmpty()) {
                Log.e(
                    TAG,
                    "Could not extract any valid fonts from <fileset> for legacy family '$familyName'"
                )

                // The caller readLegacyFamily expects a non-null Pair if the filesList isn't empty
                // We just return an empty family and empty list to satisfy the type, but it'll be
                // filtered out later.
                return Pair(
                    Fonts.Family(familyName, familyVariant, familyLang, emptyMap()),
                    emptyList(),
                )
            }

            val aliases = aliasNames.mapNotNull { aliasName ->
                val trimmedAlias = aliasName.trim()
                if (trimmedAlias.isNotEmpty()) {
                    Fonts.Alias(name = trimmedAlias, original = familyName, weight = null)
                } else {
                    null // Skip blanks...
                }
            }

            val derivedVariant = familyVariant ?: filesList.firstOrNull()?.variant
            val derivedLang = familyLang ?: filesList.firstOrNull()?.lang

            return Pair(
                Fonts.Family(
                    name = familyName,
                    fonts = fontsMap,
                    variant = derivedVariant?.trim().let { if (it.isNullOrBlank()) null else it },
                    lang = derivedLang?.trim().let { if (it.isNullOrBlank()) null else it }
                ),
                aliases
            )
        }

        private fun fromFontList(
            familyName: String,
            fontList: List<Fonts.Font>,
            lang: String?,
            variant: String?,
        ): Pair<Fonts.Family, List<Fonts.Alias>> {
            val fontsMap: MutableMap<Fonts.Weight, MutableList<Fonts.Font>> = mutableMapOf()

            fontList.forEach { font ->
                if (font.name.isBlank()) {
                    Log.w(
                        TAG,
                        "Skipping font with blank filename in family '$familyName'."
                    )
                    return@forEach // continue
                }
                fontsMap
                    .getOrPut(font.weight) { mutableListOf() }
                    .add(font)
            }

            if (fontsMap.isEmpty()) {
                Log.w(
                    TAG,
                    "Family '$familyName' from <font> list resulted in no valid fonts. Creating empty family."
                )
                return Pair(
                    Fonts.Family(familyName, variant, lang, emptyMap()),
                    emptyList()
                )
            }

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
            val weight = Fonts.Weight.fromString(
                getOptionalAttribute(parser, "weight", "${Fonts.Weight.NORMAL.weight}")
            )
            val style = getOptionalAttribute(
                parser, "style", Fonts.Font.STYLE_NORMAL
            ) ?: Fonts.Font.STYLE_NORMAL

            val filenameBuilder = StringBuilder()
            val axes = mutableListOf<Fonts.Axis>()

            while (parser.next() != XmlPullParser.END_TAG) {
                when (parser.eventType) {
                    XmlPullParser.TEXT -> parser.text?.let { filenameBuilder.append(it) }
                    XmlPullParser.START_TAG -> when (parser.name.trim()) {
                        "axis" -> try {
                            readAxis(parser).also { axes.add(it) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to read <axis> tag: ${e.message}", e)
                        }

                        else -> skip(parser)
                    }
                }
            }

            val filename = filenameBuilder.toString().trim().takeUnless { it.isEmpty() }
                ?: throw IllegalStateException("Font tag found with empty filename")

            return Fonts.Font(
                weight = weight,
                style = style,
                name = filename,
                axis = axes.takeUnless { it.isEmpty() }
            )
        }

        private fun readAxis(parser: XmlPullParser): Fonts.Axis {
            val tagStr = getRequiredAttribute(parser, "tag")
            val styleValueStr = getRequiredAttribute(parser, "stylevalue")
            skip(parser) // ignore empty axis tag

            if (tagStr.isBlank() || styleValueStr.isBlank()) {
                throw IllegalArgumentException("Axis tag found with blank 'tag' or 'stylevalue'.")
            }

            return Fonts.Axis(tag = tagStr, styleValue = styleValueStr)
        }

        private fun readNameset(parser: XmlPullParser): List<String> {
            parser.require(XmlPullParser.START_TAG, null, "nameset")

            val namesetList = mutableListOf<String>()

            while (parser.next() != XmlPullParser.END_TAG) { // Loop until </nameset>
                if (parser.eventType != XmlPullParser.START_TAG) continue

                if (parser.name.trim() == "name") {
                    // Found a <name> tag, now read its text content
                    var nameText = ""
                    if (parser.next() == XmlPullParser.TEXT) {
                        nameText = parser.text?.trim() ?: ""
                        parser.next() // Consume the text node to reach </name>
                    }
                    // parser should now be at END_TAG for </name>
                    if (parser.eventType != XmlPullParser.END_TAG || parser.name.trim() != "name") {
                        // Defensive: If not at </name>, log error or try to recover/skip.
                        // For simplicity here, we assume correct structure or rely on outer loop.
                        Log.w(
                            TAG,
                            "Expected </name> tag after reading text, but found ${parser.eventType} ${parser.name}"
                        )
                    }

                    // Add the name if it's not blank
                    if (nameText.isNotBlank()) {
                        namesetList.add(nameText)
                    }
                } else {
                    // Found an unexpected tag inside <nameset>
                    skip(parser) // Skip the unexpected tag and its content
                }
            }

            return namesetList
        }

        private fun readFileset(parser: XmlPullParser): List<Fonts.FileFont> = buildList {
            parser.require(XmlPullParser.START_TAG, null, "fileset")

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                when (parser.name.trim()) {
                    "file" -> {
                        // Read file attributes
                        val variant =
                            getOptionalAttribute(parser, "variant")?.takeUnless { it.isBlank() }
                        val lang = getOptionalAttribute(parser, "lang")?.takeUnless { it.isBlank() }

                        // Read file content
                        val fileName = buildString {
                            if (parser.next() == XmlPullParser.TEXT) {
                                append(parser.text?.trim() ?: "")
                                parser.next() // Move to end tag
                            }
                        }

                        // Validate parser state
                        if (parser.eventType != XmlPullParser.END_TAG || parser.name.trim() != "file") {
                            Log.w(
                                TAG,
                                "Expected </file> tag after reading text, found ${parser.eventType} ${parser.name}"
                            )
                        }

                        // Add file if valid
                        fileName.takeUnless { it.isEmpty() }?.let { name ->
                            add(Fonts.FileFont(name = name, variant = variant, lang = lang))
                        } ?: Log.w(TAG, "Skipping <file> tag with empty content within <fileset>")
                    }

                    else -> skip(parser)
                }
            }
        }

        private fun getRequiredAttribute(parser: XmlPullParser, name: String): String {
            return parser.getAttributeValue(null, name)
                ?: throw IllegalArgumentException("Missing required attribute: $name")
        }

        private fun getOptionalAttribute(
            parser: XmlPullParser,
            name: String,
            default: String? = null,
        ): String? {
            return parser.getAttributeValue(null, name) ?: default
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
    }
}

object NativeFontHelper {
    external fun cppRegisterFallbackFont(fontBytes: ByteArray): Boolean
}