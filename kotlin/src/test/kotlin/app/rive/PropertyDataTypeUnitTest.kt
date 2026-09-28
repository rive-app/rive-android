package app.rive

import app.rive.runtime.kotlin.core.ViewModel.PropertyDataType
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/** `input` and `any` are converter markers, never reported for a view model property. */
private val SENTINEL_CPP_VALUES = setOf(99, 100)

private const val HEADER_SUFFIX = "include/rive/data_bind/data_values/data_type.hpp"

/** Mirrors the runtime location branch in `kotlin/src/main/cpp/CMakeLists.txt`. */
private val RUNTIME_ROOTS = listOf("packages/runtime", "submodules/rive-runtime")

/** Gradle's working directory for unit tests is not contractual, so search for the header. */
private fun findDataTypeHeader(): File {
    var directory: File? = File(".").absoluteFile
    while (directory != null) {
        RUNTIME_ROOTS.forEach { root ->
            val candidate = File(directory, "$root/$HEADER_SUFFIX")
            if (candidate.isFile) return candidate
        }
        directory = directory.parentFile
    }
    error(
        "Could not find $HEADER_SUFFIX under any of $RUNTIME_ROOTS above ${File(".").absolutePath}"
    )
}

/**
 * Reads `rive::DataType` as a name to value map, failing on entries it cannot read
 */
private fun parseDataTypes(header: String): Map<String, Int> {
    val withoutComments = header.replace(
        Regex("""//[^\r\n]*|/\*[\s\S]*?\*/"""),
        ""
    )
    val body = Regex(
        """enum\s+class\s+DataType\b[^{]*\{([^}]*)}"""
    ).find(withoutComments)?.groupValues?.get(1)
        ?: error("Could not find enum class DataType")

    val entryPattern = Regex("""(\w+)\s*=\s*(\d+)""")

    return body.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .associate { entry ->
            val match = entryPattern.matchEntire(entry)
                ?: error("Unsupported DataType entry: $entry")
            match.groupValues[1] to match.groupValues[2].toInt()
        }
}

/**
 * Guards [PropertyDataType] against drift from the C++ header, which JNI feeds into the non-null
 * `ViewModel.Property.type` via `fromInt`, so an unmirrored value fails property listing.
 *
 * Values, not names: `rive::DataType` is serialized into `.riv`, so upstream can only append,
 * never renumber. Mirroring the names would add a third copy of this list for no extra coverage.
 */
class PropertyDataTypeUnitTest : FunSpec({
    test("Mirrors every rive::DataType that can be reported for a property") {
        val header = findDataTypeHeader()
        val cppValues = parseDataTypes(header.readText())
            .filterValues { it !in SENTINEL_CPP_VALUES }
        val kotlinValues = PropertyDataType.entries.map { it.value }.toSet()

        withClue("PropertyDataType drifted from ${header.path}, which declares $cppValues") {
            kotlinValues shouldBe cppValues.values.toSet()
        }
    }

    test("Assigns a distinct value to every entry") {
        // fromInt is backed by associateBy, so a duplicated value silently shadows an entry.
        val values = PropertyDataType.entries.map { it.value }

        values.distinct() shouldBe values
        PropertyDataType.entries.forEach { dataType ->
            PropertyDataType.fromInt(dataType.value) shouldBe dataType
        }
    }
})
