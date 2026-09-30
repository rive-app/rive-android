package app.rive.gradle

import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

/** Verifies the packaged AAR before publication, including when the AAR is already up to date. */
@DisableCachingByDefault(because = "Release policy must be checked on every publication")
abstract class VerifyNativeReleaseTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val readelf: RegularFileProperty

    @get:Input abstract val abis: SetProperty<String>

    /**
     * Checks all packaged native libraries using the configured NDK's inspection tool.
     *
     * @throws GradleException if inspection fails or any library violates release policy.
     */
    @TaskAction
    fun verify() {
        val verifier = NativeReleaseVerifier { library ->
            val output = ByteArrayOutputStream()
            val errors = ByteArrayOutputStream()
            val result = execOperations.exec {
                commandLine(
                    readelf.get().asFile.absolutePath,
                    "--elf-output-style=JSON",
                    "--sections",
                    "--dyn-symbols",
                    "--notes",
                    library.absolutePath,
                )
                standardOutput = output
                errorOutput = errors
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                throw GradleException(
                    "llvm-readelf failed (${result.exitValue}): ${errors.toString("UTF-8").trim()}"
                )
            }
            output.toString("UTF-8")
        }
        val violations = verifier.verifyAar(aar.get().asFile, abis.get(), temporaryDir)
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Native release verification failed:\n  ${violations.joinToString("\n  ")}"
            )
        }
        logger.lifecycle(
            "Verified stripped native libraries and JNI exports in ${aar.get().asFile}"
        )
    }
}

/** Release policy shared by the Gradle task and tests using real NDK-built ELF fixtures. */
internal class NativeReleaseVerifier(private val inspect: (File) -> String) {
    /**
     * Checks every packaged library and requires exactly one Rive and libc++ library per ABI.
     *
     * @param aar The completed release archive.
     * @param abis The ABIs requested for this build.
     * @param temporaryDirectory Directory used to hold individual libraries during inspection.
     * @return All release-policy violations, including inspection failures.
     */
    fun verifyAar(aar: File, abis: Set<String>, temporaryDirectory: File): List<String> {
        val errors = mutableListOf<String>()
        temporaryDirectory.mkdirs()
        ZipFile(aar).use { archive ->
            val libraries = archive.entries().asSequence()
                .filter { it.name.startsWith("jni/") && it.name.endsWith(".so") }.toList()
            for (abi in abis) {
                for (name in listOf("librive-android.so", "libc++_shared.so")) {
                    val path = "jni/$abi/$name"
                    if (libraries.count { it.name == path } != 1) {
                        errors += "$path: expected exactly one packaged library"
                    }
                }
            }
            libraries.forEachIndexed { index, entry ->
                val parts = entry.name.split('/')
                if (parts.size != 3 || parts[1] !in abis) {
                    errors += "${entry.name}: unexpected native library path or ABI"
                    return@forEachIndexed
                }
                // Never extract archive-controlled paths into the filesystem.
                val library = temporaryDirectory.resolve("$index.so")
                try {
                    archive.getInputStream(entry).use { input ->
                        library.outputStream().use { input.copyTo(it) }
                    }
                    errors += verifyLibrary(inspect(library), parts[2] == "librive-android.so")
                        .map { "${entry.name}: $it" }
                } catch (exception: Exception) {
                    errors += "${entry.name}: unable to inspect library: ${exception.message}"
                } finally {
                    library.delete()
                }
            }
        }
        return errors
    }

    /**
     * Interprets llvm-readelf JSON and applies the release symbol and metadata policy.
     *
     * @param json The inspection output for one ELF library.
     * @param isRive Whether the JNI-only export policy applies to this library.
     * @return All policy violations found in the inspected library.
     */
    private fun verifyLibrary(json: String, isRive: Boolean): List<String> {
        val elf = (JsonSlurper().parseText(json) as List<*>).single() as Map<*, *>
        val sections = elf.objects("Sections", "Section")
        val errors = mutableListOf<String>()
        val unwanted = sections.filter {
            val name = it.label("Name")
            it.label("Type") == "SHT_SYMTAB" || name.startsWith(".debug_") ||
                name.startsWith(".zdebug_") || name in setOf(".gdb_index", ".gnu_debugdata")
        }.map { it.label("Name") }.sorted()
        if (unwanted.isNotEmpty()) errors += "unstripped sections: ${unwanted.joinToString(", ")}"
        if (elf.objects("Notes", "NoteSection").none {
                val note = it["Note"] as? Map<*, *>
                (note?.get("Build ID") as? String).isNullOrEmpty().not()
            }
        ) {
            errors += "missing GNU build ID"
        }
        if (sections.none {
                it.label("Name") in setOf(".eh_frame", ".ARM.exidx") &&
                    (it["Size"] as Number).toLong() > 0
            }
        ) {
            errors += "missing runtime unwind information"
        }
        if (isRive) {
            // Undefined imports are resolved by other libraries and are not Rive exports.
            val exports = elf.objects("DynamicSymbols", "Symbol").filter {
                ((it["Section"] as Map<*, *>)["Value"] as Number).toLong() != 0L &&
                    it.label("Binding") in setOf("Global", "Weak")
            }.map { it.label("Name") }.toSet()
            val unexpected = exports.filter {
                it != "JNI_OnLoad" && !it.startsWith("Java_app_rive_")
            }.sorted()
            if (unexpected.isNotEmpty()) {
                errors +=
                    "unexpected dynamic exports: ${unexpected.joinToString(", ")}"
            }
            if ("JNI_OnLoad" !in exports) errors += "missing JNI_OnLoad export"
            if (exports.none { it.startsWith("Java_app_rive_") }) {
                errors +=
                    "missing Java_app_rive_* exports"
            }
        }
        return errors
    }

    /** Returns the objects inside llvm-readelf's named wrappers, failing on an unexpected schema. */
    private fun Map<*, *>.objects(key: String, wrapper: String): List<Map<*, *>> =
        (get(key) as List<*>).map { (it as Map<*, *>)[wrapper] as Map<*, *> }

    /** Returns the readable label of a structured llvm-readelf field. */
    private fun Map<*, *>.label(key: String): String = (get(key) as Map<*, *>)["Name"] as String
}
