![Build Status](https://github.com/rive-app/rive-android/actions/workflows/release.yml/badge.svg)
![Test Status](https://github.com/rive-app/rive-android/actions/workflows/tests.yml/badge.svg)
![Discord badge](https://img.shields.io/discord/532365473602600965)
![Twitter handle](https://img.shields.io/twitter/follow/rive_app.svg?style=social&label=Follow)

# Rive Android

![Rive hero image](https://cdn.rive.app/rive_logo_dark_bg.png)

An Android runtime library for [Rive](https://rive.app).

The library is distributed through
the [Maven](https://search.maven.org/artifact/app.rive/rive-android) repository.

## Table of contents

- ⭐️ [Rive Overview](#rive-overview)
- 🚀 [Getting Started & API docs](#getting-started)
- 🔍 [Supported Versions](#supported-versions)
- 🧪 [Experimental Features](#experimental-features)
- 📚 [Examples](#examples)
- 👨‍💻 [Contributing](#contributing)
- ❓ [Issues](#issues)

## Rive overview

[Rive](https://rive.app) is a real-time interactive design and animation tool that helps teams
create and run interactive animations anywhere. Designers and developers use our collaborative
editor to create motion graphics that respond to different states and user inputs. Our lightweight
open-source runtime libraries allow them to load their animations into apps, games, and websites.

🏡 [Homepage](https://rive.app/)

📘 [General help docs](https://help.rive.app/)

🛠 [Learning Rive](https://rive.app/learn-rive/)

## Getting started

Follow along with the link below for a quick start in getting Rive Android integrated into your
multi-platform applications.

[Getting Started with Rive in Android](https://rive.app/community/doc/android/docxb0vASIwp)

## Supported versions

Currently, this runtime library supports a minimum SDK version of **21**, and the target SDK version
is **33**.

## Examples

Check out the `app/` folder to see an example application using the Rive Android runtime.

To run the example app set the `app` build variant to `preview`. In Android Studio, to select which build variant to build and run, go to **Build > Select Build Variant** and select a build variant from the menu.

The `preview` build variant makes use of the hosted Rive dependency. If you're looking to contribute, set the build variant to `debug` and see `CONTRIBUTING.md` for more information. Building this variant will require additional configuration and setup.

The example showcases a number of ways to manipulate Rives, including:

- How to include Rive files into the project and reference them
- Setting layout and loop mode options
- Displaying single or multiple animations / artboards on one component
- Setting up and manipulating a state machine via inputs
- Handling events
- Utilizing a low-level API to build a render loop for more control over scenes
- ...and more!

### Awesome Rive

For even more examples and resources on using Rive at runtime or in other tools, checkout the [awesome-rive](https://github.com/rive-app/awesome-rive) repo.

## Experimental features

The Rive renderer is available _experimentally_ in `7.0.0`.

Read more about the Rive Renderer [here](https://rive.app/renderer). Additional information on [choosing a renderer](https://help.rive.app/runtimes/renderer) for Rive's runtimes.

You may encounter incompatibilities with specific devices - for example, the new renderer won't work on emulators just yet but only on physical devices.

Your feedback is greatly appreciated during this stage and we'd love to hear from you!

To use the new Rive renderer you can specify the parameter in XML:

```xml
<app.rive.runtime.kotlin.RiveAnimationView
  app:riveRenderer="Rive"
  … />
```

Alternatively, specify the renderer when initializing Rive:

```kotlin
Rive.init(applicationContext, defaultRenderer = RendererType.Rive)
```

This default value can still be overriden via XML.

## Contributing

We love contributions! Check out our [contributing docs](./CONTRIBUTING.md) to get more details into
how to run this project, the examples, and more all locally.

## Issues

Have an issue with using the runtime, or want to suggest a feature/API to help make your development
life better? Log an issue in our [issues](https://github.com/rive-app/rive-android/issues) tab! You
can also browse older issues and discussion threads there to see solutions that may have worked for
common problems.

### Known issues

After `rive-android:6.0.0`, CMake is building the library, and you might run into the following error when `rive-android` is used alongside other native libraries:

```shell
Execution failed for task ':app:mergeDebugNativeLibs'.
> A failure occurred while executing com.android.build.gradle.internal.tasks.MergeNativeLibsTask$MergeNativeLibsTaskWorkAction
   > 2 files found with path 'lib/arm64-v8a/libc++_shared.so' from inputs:
…
```

You can fix this by adding this in your `build.gradle`:

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

### Breaking changes

#### **9.0.0**

- State Machine Inputs aren't processed on the UI thread anymore, but they are queued and processed
  by the worker thread on the `advance()` following `RiveAnimationView.[fireState()/setNumberState()/setBooleanState()]`
- `RiveArtboardRenderer`
  - Constructor is simplified and now takes fewer parameters
  - Deprecated APIs from `RiveArtboardRenderer` are now accessible via `RiveAnimationView` or `RiveAnimationView.controller`
- `RiveFileController.hasPlayingAnimations` is now `isAdvancing`
