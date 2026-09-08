plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.spotless)
    id("base") // For built-in clean task
}

spotless {
    // Keep the rollout incremental from the commit immediately before Spotless was introduced.
    // Remove this in the follow-up full-formatting change that enables max-line-length enforcement.
    ratchetFrom("882c10136621e07d7992b2e4e7817e870a0169fa")

    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get())
    }
}

val clangFormatVersion = "19.1.7"
val clangFormatExecutable = providers.environmentVariable("RIVE_CLANG_FORMAT")
    .getOrElse("clang-format")
val androidNativeSources = fileTree("kotlin/src/main/cpp") {
    include("**/*.c", "**/*.cc", "**/*.cpp", "**/*.h", "**/*.hpp", "**/*.m", "**/*.mm")
    include("**/*.glsl", "**/*.vert", "**/*.frag")
    exclude("**/build/**", "**/dependencies/**")
}

val verifyClangFormatVersion = {
    val versionOutput = providers.exec {
        commandLine(clangFormatExecutable, "--version")
    }.standardOutput.asText.get().trim()
    if (!versionOutput.contains("version ${clangFormatVersion}")) {
        throw GradleException(
            "clang-format ${clangFormatVersion} is required, but found: ${versionOutput}"
        )
    }
}

tasks.register<Exec>("clangFormatApply") {
    group = "formatting"
    description = "Formats Android C++ sources with clang-format."

    doFirst {
        verifyClangFormatVersion()
        commandLine(
            listOf(
                clangFormatExecutable,
                "--style=file",
                "-i",
            ) + androidNativeSources.files.sortedBy { it.path }
        )
    }
}

tasks.register<Exec>("clangFormatCheck") {
    group = "verification"
    description = "Checks Android C++ sources with clang-format."

    doFirst {
        verifyClangFormatVersion()
        commandLine(
            listOf(
                clangFormatExecutable,
                "--dry-run",
                "--Werror",
                "--style=file",
            ) + androidNativeSources.files.sortedBy { it.path }
        )
    }
}

tasks.register("formatApply") {
    group = "formatting"
    description = "Formats Android Kotlin and C++ sources."
    dependsOn(tasks.named("spotlessApply"), tasks.named("clangFormatApply"))
}

tasks.register("formatCheck") {
    group = "verification"
    description = "Checks Android Kotlin and C++ source formatting."
    dependsOn(tasks.named("spotlessCheck"), tasks.named("clangFormatCheck"))
}

// Root clean task (gradlew or Android Studio) to clean all subprojects
tasks.named("clean") {
    dependsOn(":kotlin:clean")
}
