# Contributing

We love contributions! If you want to run the project locally to test out changes, run the examples, or just see how things work under the hood, read on below.

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

1. Install Ninja - `brew install ninja`
2. Download [Premake5](https://premake.github.io/download), and add it to your PATH

#### Steps

The Android NDK builds `.so` files for [different architectures](https://developer.android.com/ndk/guides/abis).

The current NDK version we're using is stored in [.ndk_version](./kotlin/src/main/cpp/.ndk_version). Rive is constantly making use of the latest clang features, so please ensure your NDK is up to date. ([How to install a specific NDK version](https://developer.android.com/studio/projects/install-ndk#specific-version))

Make sure you're rebuilding the native libraries when pulling in the latest changes from `rive-runtime`:

```bash
cd kotlin/src/main/cpp/

# Add NDK_PATH variable to your .zshenv
NDK_VERSION=$(tr <.ndk_version -d " \t\n\r")
echo 'export NDK_PATH=~/Library/Android/sdk/ndk/${NDK_VERSION}' >> ~/.zshenv
source ~/.zshenv

# Back to the top of the repo
cd -
# Make sure everything still builds
./gradlew assembleDebug
# After the script above completes successfully, commit your changes
git add .
```
