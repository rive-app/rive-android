import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.allopen)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dropshots)
    alias(libs.plugins.git.version)
    alias(libs.plugins.maven.publish)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosArm64()
    iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // The custom jvmShared dependsOn edges below suppress the automatic default
    // hierarchy, so apply it explicitly to keep iosMain and friends wired up.
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.androidx.annotation)
        }
        // Shared JVM-backed code (Android + desktop): the JNI bridge class and
        // native-library loading plumbing. Both targets compile the same
        // `external fun` declarations against the same JNI symbol names.
        val jvmShared by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmShared)
        jvmMain.get().dependsOn(jvmShared)
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.startup.runtime)
        }
        jvmMain.dependencies {
            // Provides Dispatchers.Main on the desktop JVM.
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.runner.junit5)
                implementation(libs.kotlin.test.junit)
                implementation(libs.mockk.agent)
                implementation(libs.mockk.android)
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                val composeBom = project.dependencies.platform(libs.androidx.compose.bom)
                implementation(composeBom)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.compose.foundation.layout)
                implementation(libs.androidx.compose.material3)
                implementation(libs.androidx.compose.ui.test.junit4)
                // See note in libs.versions.toml
                implementation(libs.androidx.test.espresso.core)
                implementation(libs.androidx.test.ext.junit.ktx)
                implementation(libs.androidx.test.runner)
                implementation(libs.kotlin.test.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

// Variant-scoped dependencies AGP still owns (build-type specific).
dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    // Used by Compose previews and UI tests
    "debugImplementation"(composeBom)
    "debugImplementation"(libs.androidx.compose.ui.test.manifest)
}

android {
    compileSdk = 36
    ndkVersion = "27.2.12479018"
    namespace = "app.rive"

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    // Enable removing miniaudio to reduce binary size
    val audioEnabled = !project.hasProperty("noAudio")
    // Enable removing scripting to reduce binary size
    val scriptingEnabled = !project.hasProperty("noScripting")
    // Enable ASAN support when debugging
    val asanEnabled = project.hasProperty("asan")

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                // Used during PR matrix builds to build each architecture as a separate job
                // Also useful for local testing
                if (project.hasProperty("abiFilters")) {
                    // Take a comma-separated string from the project property,
                    // split it into a list, and trim whitespace from each item
                    val abiList =
                        project.property("abiFilters").toString().split(",").map { it.trim() }
                    abiFilters.addAll(abiList)
                } else {
                    // Default to building all when no property is passed
                    abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
                }
                arguments.addAll(
                    listOf(
                        "-DCMAKE_VERBOSE_MAKEFILE=1",
                        "-DANDROID_ALLOW_UNDEFINED_SYMBOLS=ON",
                        "-DANDROID_CPP_FEATURES=no-exceptions no-rtti",
                        "-DANDROID_STL=c++_shared",
                        // Support for 16kb page sizes, necessary for NDK r27
                        // Can remove when upgrading to r28+
                        // https://developer.android.com/guide/practices/page-sizes#compile-r27
                        "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                        // Toggle miniaudio
                        "-DWITH_RIVE_AUDIO=${if (audioEnabled) "ON" else "OFF"}",
                        // Toggle scripting
                        "-DWITH_SCRIPTING=${if (scriptingEnabled) "ON" else "OFF"}",
                        // Needed for ASAN support (if enabled)
                        // See https://developer.android.com/ndk/guides/asan#building
                        // and https://developer.android.com/ndk/guides/cmake#android_arm_mode
                        "-DANDROID_ARM_MODE=${if (asanEnabled) "arm" else "thumb"}",
                        // Enable ASAN if requested
                        "-DENABLE_ASAN=${if (asanEnabled) "ON" else "OFF"}",
                    )
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

allOpen {
    // Allows mocking for classes without opening them for release builds
    annotation("androidx.annotation.OpenForTesting")
}

// ---- Desktop native library (librive-jvm.dylib) ------------------------------------------------
// Builds the desktop JNI library through CMake (see src/desktopNative/cpp) and packages it,
// together with MoltenVK, into the jvm target's resources so RiveNative can extract and load it.

val hostArch = System.getProperty("os.arch").let {
    if (it == "aarch64" || it == "arm64") "aarch64" else "x86_64"
}
val desktopCmakeDir = layout.buildDirectory.dir("rive-native-desktop/cmake")
// Overridable for environments with different tool locations.
val cmakeExecutable = (findProperty("rive.cmake") as String?)
    ?: android.sdkDirectory.resolve("cmake/3.22.1/bin/cmake").absolutePath
val ninjaExecutable = (findProperty("rive.ninja") as String?) ?: "/opt/homebrew/bin/ninja"
val moltenVkPath = (findProperty("rive.moltenvk") as String?)
    ?: "/opt/homebrew/lib/libMoltenVK.dylib"

val buildDesktopNative by tasks.registering {
    inputs.dir("src/desktopNative/cpp")
    inputs.dir("src/main/cpp")
    outputs.file(desktopCmakeDir.map { it.file("librive-jvm.dylib") })

    doLast {
        providers.exec {
            commandLine(
                cmakeExecutable,
                "-S", file("src/desktopNative/cpp").absolutePath,
                "-B", desktopCmakeDir.get().asFile.absolutePath,
                "-GNinja",
                "-DCMAKE_MAKE_PROGRAM=$ninjaExecutable",
            )
        }.result.get()
        providers.exec {
            commandLine(
                cmakeExecutable,
                "--build", desktopCmakeDir.get().asFile.absolutePath,
            )
        }.result.get()
    }
}

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(buildDesktopNative)
    from(desktopCmakeDir.map { it.file("librive-jvm.dylib") }) {
        into("rive-native/macos-$hostArch")
    }
    from(moltenVkPath) {
        into("rive-native/macos-$hostArch")
    }
}

// HTML output is published to api.rive.app/android/<version>/. Dokka emits a
// browsable site (nested index.html files), which is what the S3/CloudFront
// hosting expects — GFM markdown is not servable as a site.
tasks.dokkaHtml {
    // Module display name shown as the docs title/header
    moduleName.set("Rive Android")
}

// Clean up native build files. Premake output and fetched dependencies live
// under the Gradle build directory (see CMakeLists.txt), so the default clean
// already covers them. The .cxx dir is AGP's CMake workspace; the deletions of
// src/main/cpp/{out,dependencies} cover checkouts predating their relocation.
tasks.named<Delete>("clean") {
    delete(
        layout.projectDirectory.dir(".cxx"),
        layout.projectDirectory.dir("src/main/cpp/dependencies"),
        layout.projectDirectory.dir("src/main/cpp/out"),
    )
}

val PUBLISH_GROUP_ID = "app.rive"
// versionDetails() requires a .git directory, which is absent in some development
// setups (e.g. Jujutsu workspaces). Publishing is impossible there anyway, so fall
// back to a placeholder version rather than failing configuration.
val PUBLISH_VERSION = runCatching {
    val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra
    versionDetails().lastTag
}.getOrDefault("0.0.0-SNAPSHOT")
val PUBLISH_ARTIFACT_ID = "rive-android"

mavenPublishing {
    // `true` will automatically publish the artifact to Maven Central Portal.
    publishToMavenCentral(true)
    signAllPublications()
    coordinates(PUBLISH_GROUP_ID, PUBLISH_ARTIFACT_ID, PUBLISH_VERSION)

    // Mostly self-explanatory metadata
    pom {
        name.set(PUBLISH_ARTIFACT_ID)
        description.set(
            "Rive is a real-time interactive design and animation tool. Use our collaborative " +
                    "editor to create motion graphics that respond to different states and " +
                    "user inputs. Then load your animations into apps, games, and websites " +
                    "with our lightweight open-source runtimes."
        )
        url.set("https://rive.app")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/rive-app/rive-android/blob/master/LICENSE")
            }
        }

        developers {
            developer {
                id.set("erikuggeldahl")
                name.set("Erik Uggeldahl")
                email.set("erik@rive.app")
                roles.set(listOf("Android DevRel"))
            }
            developer {
                id.set("umberto-sonnino")
                name.set("Umberto Sonnino")
                email.set("umberto@rive.app")
                roles.set(listOf("Original Author"))
            }
            developer {
                id.set("luigi-rosso")
                name.set("Luigi Rosso")
                email.set("luigi@rive.app")
                roles.set(listOf("Founder", "CTO"))
            }
            developer {
                id.set("mjtalbot")
                name.set("Maxwell Talbot")
                roles.set(listOf("Original Contributor"))
            }
        }

        // Version control info
        scm {
            connection.set("scm:git:git@github.com:rive-app/rive-android.git")
            developerConnection.set("scm:git:ssh://git@github.com:rive-app/rive-android.git")
            url.set("https://github.com/rive-app/rive-android/")
        }
    }
}
