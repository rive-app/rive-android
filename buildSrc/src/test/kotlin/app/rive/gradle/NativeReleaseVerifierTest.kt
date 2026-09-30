package app.rive.gradle

import groovy.json.JsonSlurper
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Exercises the publication policy with real ARM64 ELF files built by the Android NDK. */
class NativeReleaseVerifierTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var directory: File
    private lateinit var tools: File
    private lateinit var libcxx: File

    /** Builds optimized fixture libraries with and without Rive's export restrictions. */
    @Before
    fun createFixtures() {
        directory = temporary.root
        val ndk =
            File(
                requireNotNull(System.getProperty("fixtureNdk")?.takeIf { it.isNotBlank() }) {
                    "Set ANDROID_NDK_HOME to run the native release verifier tests"
                }
            )
        tools = ndk.resolve("toolchains/llvm/prebuilt").listFiles()!!.single().resolve("bin")
        val source = directory.resolve("fixture.c")
        source.writeText(
            """
            int rive_internal(void) { return 42; }
            int JNI_OnLoad(void) { return rive_internal(); }
            int Java_app_rive_test(void) { return rive_internal(); }
            """.trimIndent()
        )
        val script = directory.resolve("exports.map")
        script.writeText("{ global: JNI_OnLoad; Java_app_rive_*; local: *; };\n")
        for (restricted in listOf(true, false)) {
            val name = if (restricted) "restricted" else "exported"
            val command = mutableListOf(
                tool("clang"), "--target=aarch64-linux-android21", "-shared", "-nostdlib",
                "-fPIC", "-O2", "-g", "-funwind-tables", "-Wl,--build-id=sha1",
                source.path, "-o", directory.resolve("$name.so").path,
            )
            if (restricted) command += "-Wl,--version-script=${script.path}"
            run(command)
            run(
                listOf(
                    tool("llvm-strip"),
                    "--strip-unneeded",
                    "-o",
                    directory.resolve("$name-stripped.so").path,
                    directory.resolve("$name.so").path
                )
            )
        }
        libcxx = tools.parentFile.resolve("sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
        run(
            listOf(
                tool("llvm-strip"),
                "--strip-unneeded",
                "-o",
                directory.resolve("libc++_shared.so").path,
                libcxx.path
            )
        )
    }

    /** Accepts a properly stripped release with JNI-only exports. */
    @Test
    fun strippedRelease() {
        assertEquals(emptyList(), checkArchive())
    }

    /** Rejects the original output that AGP could copy after a stripping failure. */
    @Test
    fun unstrippedRive() {
        val errors = checkArchive(rive = "restricted.so").joinToString("\n")
        assertTrue(errors.contains(".symtab"))
        assertTrue(errors.contains(".debug_info"))
    }

    /** Checks dependency libraries as well as Rive's own library. */
    @Test
    fun unstrippedLibcxx() {
        libcxx.copyTo(directory.resolve("libcxx-unstripped.so"))
        assertTrue(
            checkArchive(libc = "libcxx-unstripped.so").joinToString("\n")
                .contains("libc++_shared.so: unstripped sections: .symtab")
        )
    }

    /** Detects a missing linker version script even when stripping succeeds. */
    @Test
    fun exportedCoreSymbols() {
        val errors = checkArchive(rive = "exported-stripped.so").joinToString("\n")
        assertTrue(errors.contains("unexpected dynamic exports: rive_internal"))
        assertFalse(errors.contains("unstripped sections"))
    }

    /** Rejects archives missing the runtime library. */
    @Test
    fun missingLibrary() {
        assertTrue(checkArchive(rive = null).joinToString("\n").contains("expected exactly one"))
    }

    /** Prevents an incomplete ABI set from passing release verification. */
    @Test
    fun missingAbi() {
        assertTrue(
            checkArchive(abis = setOf("arm64-v8a", "x86")).joinToString("\n")
                .contains("jni/x86/librive-android.so")
        )
    }

    /** Fails closed when llvm-readelf cannot inspect a packaged library. */
    @Test
    fun invalidElf() {
        directory.resolve("invalid.so").writeText("not an ELF")
        assertTrue(
            checkArchive(
                rive = "invalid.so"
            ).joinToString("\n").contains("unable to inspect library")
        )
    }

    /** Requires the metadata needed to match symbols and unwind crash reports. */
    @Test
    fun missingBuildIdAndUnwindInformation() {
        run(
            listOf(
                tool("llvm-objcopy"),
                "--remove-section=.note.gnu.build-id",
                "--remove-section=.eh_frame",
                "--remove-section=.eh_frame_hdr",
                directory.resolve("restricted-stripped.so").path,
                directory.resolve("missing-metadata.so").path
            )
        )
        val errors = checkArchive(rive = "missing-metadata.so").joinToString("\n")
        assertTrue(errors.contains("missing GNU build ID"))
        assertTrue(errors.contains("missing runtime unwind information"))
    }

    /** Extracts both libraries, preserves build outputs, and removes stale ABI files. */
    @Test
    fun matchingSymbols() {
        val source = directory.resolve("restricted.so").readBytes()
        val stale = directory.resolve("symbols/x86/stale.debug")
        stale.parentFile.mkdirs()
        stale.writeText("stale")
        val symbols = extractSymbols()
        assertFalse(stale.exists())
        assertContentEquals(
            source,
            directory.resolve("merged/lib/arm64-v8a/librive-android.so").readBytes()
        )
        val manifest = JsonSlurper().parse(symbols.resolve("manifest.json")) as Map<*, *>
        assertEquals(
            setOf("schemaVersion", "releaseVersion", "ndkVersion", "libraries"),
            manifest.keys
        )
        assertEquals(1, manifest["schemaVersion"])
        assertEquals("test-release", manifest["releaseVersion"])
        assertEquals("test-ndk", manifest["ndkVersion"])
        val libraries = manifest["libraries"] as List<*>
        assertEquals(2, libraries.size)
        for (entry in libraries) {
            val library = entry as Map<*, *>
            assertEquals(
                setOf("abi", "library", "file", "buildId", "debugInformation"),
                library.keys
            )
            assertEquals("arm64-v8a", library["abi"])
            assertEquals("arm64-v8a/${library["library"]}.debug", library["file"])
            assertTrue((library["buildId"] as String).isNotEmpty())
            assertTrue(symbols.resolve(library["file"] as String).isFile)
        }
        assertEquals("dwarf", (libraries[0] as Map<*, *>)["debugInformation"])
        assertEquals("symbol-table", (libraries[1] as Map<*, *>)["debugInformation"])
    }

    /** Rejects companions from a different build even when ABI and filenames agree. */
    @Test
    fun mismatchedSymbols() {
        val error = assertFailsWith<IllegalStateException> { extractSymbols(rive = "exported.so") }
        assertTrue(error.message.orEmpty().contains("build ID differs"))
        assertFalse(directory.resolve("symbols/manifest.json").exists())
    }

    /** Requires source-level debug information rather than accepting only Rive function names. */
    @Test
    fun missingDwarf() {
        run(
            listOf(
                tool("llvm-objcopy"),
                "--strip-debug",
                directory.resolve("restricted.so").path,
                directory.resolve("no-dwarf.so").path
            )
        )
        val error = assertFailsWith<IllegalStateException> { extractSymbols(rive = "no-dwarf.so") }
        assertTrue(error.message.orEmpty().contains("missing DWARF"))
    }

    /** Rejects libc++ whose symbol table was already stripped before extraction. */
    @Test
    fun missingDependencySymbols() {
        val error = assertFailsWith<IllegalStateException> {
            extractSymbols(libc = directory.resolve("libc++_shared.so"))
        }
        assertTrue(error.message.orEmpty().contains("missing full symbol table"))
    }

    /** Creates AGP-shaped inputs and extracts companions matching the fixture AAR. */
    private fun extractSymbols(rive: String = "restricted.so", libc: File = libcxx): File {
        checkArchive()
        val merged = directory.resolve("merged")
        val abi = merged.resolve("lib/arm64-v8a")
        abi.mkdirs()
        directory.resolve(rive).copyTo(abi.resolve("librive-android.so"))
        libc.copyTo(abi.resolve("libc++_shared.so"))
        val output = directory.resolve("symbols")
        NativeSymbolExtractor(::run).extract(
            merged,
            directory.resolve("fixture.aar"),
            File(tool("llvm-readelf")),
            File(tool("llvm-objcopy")),
            setOf("arm64-v8a"),
            "test-release",
            "test-ndk",
            output
        )
        return output
    }

    /** Packages fixtures as an AAR and returns all verifier diagnostics. */
    private fun checkArchive(
        rive: String? = "restricted-stripped.so",
        libc: String = "libc++_shared.so",
        abis: Set<String> = setOf("arm64-v8a"),
    ): List<String> {
        val aar = directory.resolve("fixture.aar")
        ZipOutputStream(aar.outputStream()).use { archive ->
            val libraries = mutableMapOf("libc++_shared.so" to libc)
            if (rive != null) libraries["librive-android.so"] = rive
            libraries.forEach { (name, file) ->
                archive.putNextEntry(ZipEntry("jni/arm64-v8a/$name"))
                directory.resolve(file).inputStream().use { it.copyTo(archive) }
                archive.closeEntry()
            }
        }
        val verifier = NativeReleaseVerifier { library ->
            run(
                listOf(
                    tool("llvm-readelf"),
                    "--elf-output-style=JSON",
                    "--sections",
                    "--dyn-symbols",
                    "--notes",
                    library.path
                )
            )
        }
        return verifier.verifyAar(aar, abis, directory.resolve("inspection"))
    }

    /** Resolves the NDK executable on Unix or Windows hosts. */
    private fun tool(name: String): String =
        tools.resolve(name).takeIf { it.isFile }?.path ?: tools.resolve("$name.exe").path

    /** Runs an NDK fixture command and includes diagnostic output on failure. */
    private fun run(command: List<String>): String {
        // Debug companions can produce benign readelf warnings. Keep them out of its JSON output.
        val diagnostics = directory.resolve("tool-stderr.txt")
        val process = ProcessBuilder(command).redirectError(diagnostics).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) {
            "Command failed: $command\n$output\n${diagnostics.readText()}"
        }
        return output
    }
}
