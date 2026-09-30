package app.rive.gradle

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

/** Extracts native debug companions from the same unstripped libraries used to package the AAR. */
@DisableCachingByDefault(because = "Native symbols are large release artifacts stored locally")
abstract class ExtractNativeSymbolsTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedLibraries: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val readelf: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val objcopy: RegularFileProperty

    @get:Input abstract val abis: SetProperty<String>

    @get:Input abstract val releaseVersion: Property<String>

    @get:Input abstract val ndkVersion: Property<String>

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    /**
     * Extracts companions and verifies their build IDs against the actual packaged libraries.
     *
     * @throws GradleException if extraction fails, debug information is missing, or build IDs differ.
     */
    @TaskAction
    fun extract() {
        NativeSymbolExtractor { command ->
            val output = ByteArrayOutputStream()
            val errors = ByteArrayOutputStream()
            val result = execOperations.exec {
                commandLine(command)
                standardOutput = output
                errorOutput = errors
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                throw GradleException(
                    "Native tool failed (${result.exitValue}): ${errors.toString("UTF-8").trim()}"
                )
            }
            output.toString("UTF-8")
        }.extract(
            mergedLibraries.get().asFile,
            aar.get().asFile,
            readelf.get().asFile,
            objcopy.get().asFile,
            abis.get(),
            releaseVersion.get(),
            ndkVersion.get(),
            outputDirectory.get().asFile,
        )
    }
}

/** Extracts and validates release companions without modifying the unstripped build outputs. */
internal class NativeSymbolExtractor(private val run: (List<String>) -> String) {
    /**
     * Produces one companion per library and ABI plus a manifest of matching build IDs.
     *
     * @param mergedLibraries AGP's unstripped merged native libraries directory.
     * @param aar The completed release AAR used to verify build IDs.
     * @param readelf The selected NDK's ELF inspection executable.
     * @param objcopy The selected NDK's object copying executable.
     * @param abis The requested release ABIs.
     * @param releaseVersion The SDK version recorded in the manifest.
     * @param ndkVersion The toolchain version recorded in the manifest.
     * @param outputDirectory Directory containing only the resulting companions and manifest.
     * @throws IllegalStateException if required metadata is missing or does not match.
     */
    fun extract(
        mergedLibraries: File,
        aar: File,
        readelf: File,
        objcopy: File,
        abis: Set<String>,
        releaseVersion: String,
        ndkVersion: String,
        outputDirectory: File,
    ) {
        check(abis.isNotEmpty()) { "No release ABIs requested" }
        check(!outputDirectory.exists() || outputDirectory.deleteRecursively()) {
            "Cannot clear $outputDirectory"
        }
        check(outputDirectory.mkdirs()) { "Cannot create $outputDirectory" }
        val libraries = mutableListOf<ManifestLibrary>()
        ZipFile(aar).use { archive ->
            for (abi in abis.sorted()) {
                for (name in listOf("librive-android.so", "libc++_shared.so")) {
                    val input = mergedLibraries.resolve("lib/$abi/$name")
                    check(input.isFile) { "Missing unstripped library: $input" }
                    val packagedPath = "jni/$abi/$name"
                    val entry = archive.entries().asSequence().filter {
                        it.name == packagedPath
                    }.single()
                    val sourceInfo = inspect(readelf, input)
                    val buildId = sourceInfo.buildId
                    val packed = outputDirectory.resolve("packaged.so")
                    try {
                        archive.getInputStream(entry).use { stream ->
                            packed.outputStream().use { stream.copyTo(it) }
                        }
                        check(inspect(readelf, packed).buildId == buildId) {
                            "$packagedPath: build ID differs from unstripped input"
                        }
                    } finally {
                        packed.delete()
                    }
                    val companionPath = "$abi/$name.debug"
                    val companion = outputDirectory.resolve(companionPath)
                    companion.parentFile.mkdirs()
                    run(listOf(objcopy.path, "--only-keep-debug", input.path, companion.path))
                    val companionInfo = inspect(readelf, companion)
                    check(companionInfo.buildId == buildId) {
                        "$companionPath: extracted build ID differs"
                    }
                    check(companionInfo.hasSymbols) { "$companionPath: missing full symbol table" }
                    if (name == "librive-android.so") {
                        check(companionInfo.hasDwarf) {
                            "$companionPath: missing DWARF debug information"
                        }
                    }
                    libraries += ManifestLibrary(
                        abi = abi,
                        library = name,
                        file = companionPath,
                        buildId = buildId,
                        debugInformation =
                            if (companionInfo.hasDwarf) "dwarf" else "symbol-table",
                    )
                }
            }
        }
        val manifest = SymbolsManifest(
            schemaVersion = 1,
            releaseVersion = releaseVersion,
            ndkVersion = ndkVersion,
            libraries = libraries
        )
        outputDirectory.resolve(
            "manifest.json"
        ).writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n")
    }

    /** Returns the matching identifier and available debug information for an ELF file. */
    private fun inspect(readelf: File, library: File): SymbolInfo {
        val json =
            run(
                listOf(
                    readelf.path,
                    "--elf-output-style=JSON",
                    "--sections",
                    "--notes",
                    library.path
                )
            )
        val elf = (JsonSlurper().parseText(json) as List<*>).single() as Map<*, *>
        val sections = (elf["Sections"] as List<*>).map {
            (it as Map<*, *>)["Section"] as Map<*, *>
        }
        val names = sections.filter { (it["Size"] as Number).toLong() > 0 }
            .map { ((it["Name"] as Map<*, *>)["Name"] as String) }.toSet()
        val buildIds = (elf["Notes"] as List<*>).mapNotNull {
            val section = (it as Map<*, *>)["NoteSection"] as Map<*, *>
            (section["Note"] as? Map<*, *>)?.get("Build ID") as? String
        }
        val buildId = buildIds.singleOrNull()?.takeIf { it.isNotEmpty() }
        check(buildId != null) { "$library: missing unique GNU build ID" }
        return SymbolInfo(
            buildId,
            ".symtab" in names,
            ".debug_info" in names && ".debug_line" in names
        )
    }

    /** Metadata retained in the companion and used to match it to the shipped library. */
    private data class SymbolInfo(
        val buildId: String,
        val hasSymbols: Boolean,
        val hasDwarf: Boolean,
    )
}

/** Published manifest schema, serialized through its Kotlin property getters. */
internal data class SymbolsManifest(
    val schemaVersion: Int,
    val releaseVersion: String,
    val ndkVersion: String,
    val libraries: List<ManifestLibrary>,
)

/** One debug companion and the metadata needed to match it to a packaged library. */
internal data class ManifestLibrary(
    val abi: String,
    val library: String,
    val file: String,
    val buildId: String,
    val debugInformation: String,
)
