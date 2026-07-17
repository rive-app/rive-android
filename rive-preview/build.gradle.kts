// Resources-only artifact carrying the desktop Rive native libraries.
// Consumers add it as debugImplementation so Android Studio previews (layoutlib,
// which runs the Android classpath on a host JVM) can load and render Rive.
//
// This is an Android library (not java-library) on purpose: layoutlib's render
// classpath exposes an Android module's java resources, whereas a plain JVM
// module's resources are invisible to preview classloaders.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.rive.preview"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/riveNatives"))
    sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated/riveNativeSrc"))
}

val hostArch = System.getProperty("os.arch").let {
    if (it == "aarch64" || it == "arm64") "aarch64" else "x86_64"
}
val moltenVkPath = (findProperty("rive.moltenvk") as String?)
    ?: "/opt/homebrew/lib/libMoltenVK.dylib"

val stagedNativesDir = layout.buildDirectory.dir("generated/riveNatives/rive-native/macos-$hostArch")

val stageRiveNatives = tasks.register<Copy>("stageRiveNatives") {
    dependsOn(project(":kotlin").tasks.named("buildDesktopNative"))
    from(project(":kotlin").layout.buildDirectory.file("rive-native-desktop/cmake/librive-jvm.dylib"))
    from(moltenVkPath)
    into(stagedNativesDir)
    // Homebrew's MoltenVK ships read-only; writable copies keep incremental
    // re-copies (and downstream java-res processing) from failing.
    fileMode = 0b110_100_100 // 0644
}

// Android Studio's preview classloader (StudioModuleClassLoader) refuses classpath *resource*
// lookups but loads *classes* normally, so the absolute path of the staged natives is baked
// into a generated class. Previews always run on the machine that built the project, so an
// absolute build-tree path is valid there.
val generateRiveNativePaths = tasks.register("generateRiveNativePaths") {
    val outDir = layout.buildDirectory.dir("generated/riveNativeSrc")
    val nativeDirPath = stagedNativesDir.get().asFile.absolutePath
    inputs.property("nativeDirPath", nativeDirPath)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("app/rive/preview/RiveNativePaths.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package app.rive.preview;

            /** Generated at build time; see rive-preview/build.gradle.kts. */
            public final class RiveNativePaths {
                public static final String NATIVE_DIR = "$nativeDirPath";

                private RiveNativePaths() {}
            }
            """.trimIndent()
        )
    }
}

tasks.matching { it.name.matches(Regex("process\\w*JavaRes")) }.configureEach {
    dependsOn(stageRiveNatives)
}
tasks.matching {
    it.name.matches(Regex("(compile\\w*JavaWithJavac|javaPreCompile|merge\\w*Sources|extract\\w*Annotations|\\w*sourcesJar\\w*|lint\\w*)"))
}.configureEach {
    dependsOn(generateRiveNativePaths)
}
