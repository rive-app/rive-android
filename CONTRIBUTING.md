# Contributing

We love contributions! If you want to run the project locally to test out changes, run the examples, or just see how things work under the hood, read on below.

## Getting the Source

The C++ runtime is a submodule, so clone with it:

```shell
git clone --recursive git@github.com:rive-app/rive-android.git
```

If you have already cloned without `--recursive`, initialize it before building:

```shell
git submodule update --init --recursive
```

## Project Layout

### `/kotlin`

This is the main module of our Android library. Here you can find the [`RiveAnimationView`](https://github.com/rive-app/rive-android/blob/master/kotlin/src/main/java/app/rive/runtime/kotlin/RiveAnimationView.kt) entrypoint in the [`app.rive.runtime.kotlin`](https://github.com/rive-app/rive-android/tree/master/kotlin/src/main/java/app/rive/runtime/kotlin) namespace.

The underlying [C++ runtime](https://github.com/rive-app/rive-runtime) is mapped to classes in the [`app.rive.runtime.kotlin.core`](https://github.com/rive-app/rive-android/tree/master/kotlin/src/main/java/app/rive/runtime/kotlin/core) namespace. These allow more fine grained control of the Rive file state. `RiveAnimationView` is built on top of this.

#### `/cpp` and `/submodules`

This runtime is built on top of our [C++ runtime](https://github.com/rive-app/rive-runtime). This is included as a submodule in [`/submodules`](https://github.com/rive-app/rive-android/tree/master/submodules). The [`/cpp`](https://github.com/rive-app/rive-android/tree/master/kotlin/src/main/cpp) folder contains the C++ side of our Android bindings.

### `/app`

Multiple sample activities can be found here. This can be a useful reference for getting started using the runtime.

## Development Workflow

### Running Locally

#### Gradle

From the project root, run:

```shell
./gradlew :app:bundleDebug
```

#### Android Studio

In Android Studio, ensure the `app` build variant is set to `debug` (or manually update the `build.gradle` dependencies to use the local Rive runtime as a resource).

To select which build variant to build and run, go to **Build > Select Build Variant...** and select a build variant from the menu.

### Formatting

From the project root, format or check all Android Kotlin and C++ sources with:

```shell
./gradlew formatApply
./gradlew formatCheck
```

These aggregate tasks preserve the canonical formatter for each language: Spotless with ktlint for
Kotlin, and clang-format 19.1.7 for C++. Set `RIVE_CLANG_FORMAT` to the clang-format executable when
it is not available on `PATH`.

#### Kotlin

Spotless with ktlint is the canonical Kotlin formatter. It uses ktlint's Android Studio style and
requires trailing commas on declaration and call sites. From the project root, run:

```shell
./gradlew spotlessApply
./gradlew spotlessCheck
```

`spotlessApply` formats changed Kotlin source files, and `spotlessCheck` is the same check enforced
by CI. The rollout is ratcheted from commit `882c10136621e07d7992b2e4e7817e870a0169fa`, so existing
files are formatted in full when they are first changed instead of producing a repository-wide
formatting commit. CI checks out full Git history so the fixed baseline is always available. Once
all Kotlin files have passed through the ratchet, the baseline can be removed to enforce the whole
Android source tree directly.

Android Studio users can install the ktlint plugin and optionally enable ktlint formatting on save.
The plugin reads the project `.editorconfig`, including the Android Studio style and trailing-comma
policy used by Spotless. A Gradle run configuration for `spotlessApply` is another convenient
integration. IDE and plugin versions can differ, so Spotless output remains authoritative.
`.editorconfig` aligns shared IDE and ktlint behavior, but does not enforce formatting and is not a
substitute for the Gradle check.

#### C++

The repository `.clang-format` is the canonical C++ formatter configuration. The Android-specific
`.clang-format` inherits it and customizes include sorting. To format or check only Android C++
sources, run:

```shell
./gradlew clangFormatApply
./gradlew clangFormatCheck
```

In Android Studio, enable ClangFormat for C/C++ files. The IDE's built-in code-style scheme and its
hard-wrap setting do not replace the repository ClangFormat configuration.

### Testing

After making any changes to the source code, be sure to run the test suite.

#### Gradle

From the project root, run:

```shell
./gradlew 
```

#### Android Studio

- Select the "Project" view (upper-right corner)
- Right-click on `kotlin/src/androidTest` and select "Run All Tests"

### Building `.so` Files

The runtime here should be updated to point to the latest `rive-runtime` submodule when that runtime has new commits merged in. This ensures the `rive-android` project is up-to-date with its underlying native code layer to pull in latest patches, features, and more. In most cases, when new `rive-runtime` changes are introduced, we need to build new `.so` files for different architectures.

#### Pre-requisites

1. Install Ninja and the shader toolchain - `brew install ninja glslang`
2. Install NDK - Gradle auto-downloads the version pinned by `ndkVersion` in `kotlin/build.gradle.kts`, but [only if the SDK licenses are already accepted](https://developer.android.com/studio/projects/install-ndk#auto-download-ndk). Accept them once with `sdkmanager --licenses`, or install the NDK yourself with `sdkmanager --install "ndk;{ndk-version}"`.

#### Steps

The Android NDK builds `.so` files for [different architectures](https://developer.android.com/ndk/guides/abis).

Rive is constantly making use of the latest clang features, and the C++ runtime checks it independently and fails the build on a mismatch.

Make sure you're rebuilding the native libraries when pulling in the latest changes from `rive-runtime`:

```bash
# Make sure everything still builds
./gradlew assembleDebug
# After the command above completes successfully, commit your changes
git add .
```
