[![Build Status](https://github.com/rive-app/rive-android/actions/workflows/release.yml/badge.svg?style=flat-square)](https://github.com/rive-app/rive-android/releases)
[![Discord Badge](https://img.shields.io/discord/532365473602600965)](https://discord.gg/dpRpR7jH)
[![Twitter Handle](https://img.shields.io/twitter/follow/rive_app.svg?style=social&label=Follow)](https://x.com/rive_app)

# Rive Android

[![Rive hero image](https://cdn.rive.app/rive_logo_dark_bg.png)](https://rive.app)

The [Rive](https://rive.app) runtime for Android.

This library is distributed through [Maven](https://central.sonatype.com/artifact/app.rive/rive-android).

## Table of Contents

- ⭐️ [Rive Overview](#rive-overview)
- 🚀 [Getting Started & API docs](#getting-started)
- 🔍 [Supported Versions](#supported-versions)
- 📚 [Examples](#examples)
- 👨‍💻 [Contributing](#contributing)
- ❓ [Filing Issues](#issues)
- 🧰 [Troubleshooting](#troubleshooting)

## Rive Overview

[Rive](https://rive.app) is a real-time, collaborative design and animation tool that helps teams create and run interactive, animated graphics anywhere. Designers and developers work together in the editor to create a Rive file that responds to different states and user inputs. Our lightweight open-source runtime libraries allow them to load this file into apps, games, and websites.

📘 [Rive Docs](https://rive.app/docs/) | 🛠 [Rive Community](https://community.rive.app/)

## Getting Started

To get started with Rive Android, check the [Android section](https://rive.app/docs/runtimes/android/android) of the runtime docs.

For more information, see the [Runtime](https://rive.app/docs/runtimes/getting-started) sections, such as:

- [Artboards](https://rive.app/docs/runtimes/artboards)
- [Layout](https://rive.app/docs/runtimes/layout)
- [State Machines](https://rive.app/docs/runtimes/state-machines)
- [Data Binding](https://rive.app/docs/runtimes/data-binding)
- [Loading Assets](https://rive.app/docs/runtimes/loading-assets)

## Supported Versions

Currently, this runtime library supports a minimum SDK version of **21**, and the target SDK version is **35**.

## Building

The build system begins with Gradle, but also includes CMake and Premake at lower levels.

To build the Rive Android library from the Gradle CLI, use the following:

`./gradlew :kotlin:assembleRelease`

This will produce `kotlin/build/outputs/aar/kotlin-release.aar`, which is equivalent to the Android Archive (AAR) file published on Maven.

### Build Options

For advanced use cases, you may want to consider building from source for variants that we do not publish.

#### Building Particular Architectures

You may want to produce an AAR with only particular application binary interfaces (ABIs) included. By default we produce for all four common variants: `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`. To choose one, use the following:

`./gradlew :kotlin:assembleRelease -PabiFilters=arm64-v8a`

You can also build for multiple with a comma separated list:

`./gradlew :kotlin:assembleRelease -PabiFilters="arm64-v8a,armeabi-v7a"`

#### No Audio Engine

Rive Android includes an audio engine by default by linking miniaudio. This allows for playback of audio assets from Rive files. If you do not need this functionality and your goal is to minimize binary size, you can exclude it and save 600kb with the following:

`./gradlew :kotlin:assembleRelease -PnoAudio`

## Examples

Check out the `app/` folder to see an example application using the Rive Android runtime.

To run the example app set the `app` build variant to `preview`. In Android Studio, to select which build variant to build and run, go to **Build > Select Build Variant** and select a build variant from the menu.

The `preview` build variant makes use of the hosted Rive dependency. If you're looking to contribute, set the build variant to `debug` and see `CONTRIBUTING.md` for more information. Building this variant will require additional configuration and setup.

The example showcases a number of ways to manipulate Rive files, including:

- How to include Rive files in a project and reference them
- Setting layout and loop mode options
- Displaying single or multiple artboards in one component
- Setting up and manipulating a state machine via inputs
- Handling events
- Using a low-level API to build a render loop for more control over scenes
- ... and more!

### Community Examples

For even more examples and resources on using Rive at runtime or in other tools, checkout the [Awesome Rive](https://github.com/rive-app/awesome-rive) repo or check out our [community](https://community.rive.app/).

## Contributing

We love contributions! Check out our [contributing docs](./CONTRIBUTING.md) to get more details into how to run this project locally.

## Filing Issues

Have an issue with using the runtime, or want to suggest a feature or API to help make your development life better? Log an issue in our [issues](https://github.com/rive-app/rive-android/issues) tab! You can also browse older issues and discussion threads there to see solutions that may have worked for common problems.

## Troubleshooting

Rive Android uses CMake to build the library. You might run into the following error when Rive Android is used alongside other native libraries:

```shell
Execution failed for task ':app:mergeDebugNativeLibs'.
> A failure occurred while executing com.android.build.gradle.internal.tasks.MergeNativeLibsTask$MergeNativeLibsTaskWorkAction
   > 2 files found with path 'lib/arm64-v8a/libc++_shared.so' from inputs:
…
```

This is due to both dependencies attempting to include their version of the C++ standard library. You can fix this by prioritizing one by adding this in your `build.gradle`:

```gradle
android {
  …
  packagingOptions {
      pickFirst "lib/x86/libc++_shared.so"
      pickFirst "lib/x86_64/libc++_shared.so"
      pickFirst "lib/armeabi-v7a/libc++_shared.so"
      pickFirst "lib/arm64-v8a/libc++_shared.so"
  }
  …
}
```
