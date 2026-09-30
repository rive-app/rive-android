# Native release verification

`./gradlew :kotlin:verifyReleaseNativeLibraries` builds and inspects the release AAR. All Maven publication tasks depend on this check, including local publication and Vanniktech's Maven Central staging publication. The Kotlin Gradle task uses `llvm-readelf` from the NDK selected by AGP. No separate scripting runtime is required.

`buildSrc/src/main/kotlin` is Gradle's conventional directory for Kotlin build logic. Gradle compiles it automatically and exposes its classes to the project build scripts. Tests live in `buildSrc/src/test/kotlin`.

The check rejects full symbol tables, DWARF debug sections, and unexpected Rive dynamic exports. It also requires the JNI exports, build IDs, runtime unwind information, and one Rive and libc++ library per requested ABI. The export policy matches `kotlin/src/main/cpp/rive-android.map.txt` and must be updated deliberately if the library gains a new public native interface.

AGP can warn and package an unstripped library when stripping fails. Checking the finished AAR makes that a publication failure. Keep the linker version script as well: stripping alone does not remove exported core symbols.

For ARM64 local iterations:

```sh
./gradlew :kotlin:verifyReleaseNativeLibraries -PabiFilters=arm64-v8a
```

Omit `abiFilters` for release verification of all four supported ABIs.

The verifier tests compile small ARM64 ELF fixtures and exercise both acceptance and rejection, including an unstripped dependency and exported core symbols:

```sh
ANDROID_NDK_HOME=/path/to/ndk/27.2.12479018 ./gradlew :buildSrc:test
```

The root `spotlessApply` and `spotlessCheck` tasks also cover these Kotlin sources.

## Release symbols ZIP

```sh
./gradlew :kotlin:nativeSymbolsZip -PabiFilters=arm64-v8a
```

Omit `abiFilters` to generate the complete release artifact. The ZIP is written to `kotlin/build/outputs/native-symbols/rive-android-<version>-native-symbols.zip`. It contains `<abi>/librive-android.so.debug`, `<abi>/libc++_shared.so.debug`, and `manifest.json`, which records the SDK and NDK versions and each library's build ID and available debug information.

The Android Premake entry point enables release debug information for the core, renderer, and static dependencies without changing optimization or LTO. CMake also retains debug information for the JNI library. Both builds remap the checkout root to `.` in source paths so published Rive debug information uses repository-relative paths. `extractReleaseNativeSymbols` reads AGP's `MERGED_NATIVE_LIBS` output and runs the selected NDK's `llvm-objcopy --only-keep-debug`. It never modifies these inputs. AGP independently strips the libraries packaged in the AAR.

ZIP creation depends on release-AAR verification. Extraction additionally requires matching build IDs in the unstripped input, packaged library, and debug companion. Rive's companion must include a symbol table and DWARF information. libc++ must include a symbol table, but DWARF is not required because NDK r27c does not supply it. The manifest describes the actual information present. If a consuming app packages a different libc++, it must obtain symbols matching that binary's build ID.

## Maven publication and retrieval

The ZIP is attached to the existing `app.rive:rive-android:<version>` publication with classifier `native-symbols` and extension `zip`. The existing Maven publishing and signing tasks include it automatically. Publishing builds the ZIP and runs its extraction and verification dependencies. Release CI must omit `abiFilters` so the archive includes all four supported ABIs.

The ZIP is an explicitly requested companion artifact. Normal AAR dependencies do not download it or package it into the app. Configure `google()` and `mavenCentral()` in the project's dependency repositories, typically in `settings.gradle.kts` under `dependencyResolutionManagement.repositories`, so Gradle can resolve the publication and its referenced metadata. To retrieve and extract it in a consuming project's Kotlin build script:

```kotlin
val riveVersion = "<version>" // Use the same version as the app's Rive dependency.
val riveNativeSymbols by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    riveNativeSymbols("app.rive:rive-android:$riveVersion:native-symbols@zip")
}

tasks.register<Sync>("extractRiveNativeSymbols") {
    from(provider { riveNativeSymbols.map { zipTree(it) } })
    into(layout.buildDirectory.dir("rive-native-symbols"))
}
```

Run `./gradlew extractRiveNativeSymbols` and upload the extracted companions to the crash-reporting project used by the app. Match the manifest's build IDs to the app's packaged libraries, especially if another dependency supplies its own libc++.
