# Rive-Android

This is the Android runtime for [Rive 2](https://beta.rive.app), currently in Beta.

## Setup

1. Clone the repo
2. Initialize the submodule
3. Open the directory as an Android Studio project
4. Run the example app

### Overview

This runtime is using [rive-cpp](https://github.com/rive-app/rive-cpp) as its base layer and leverages the NDK. 

The `/kotlin` folder contains the library code, with the interface to the C++ layer. 

The `/cpp` folder contains the C++ code that uses the JNI to interface with the library.

The `/app` folder contains an example app that builds an Activity with a custom View, and shows a sample animation running. `AnimationView.kt` contains a good example of how to set up a rendering loop with a Rive file.

### Using the library

The `/kotlin` subfolder is its own module in the Android Studio project and it can generate an android archive (`.aar`). 

With the Android Studio project open, in the menu select **`Build` > `Make Module 'kotlin'`**.
Android Studio will put the `.aar` library file in `kotlin/build/outputs/aar`.

[The documentation](https://developer.android.com/studio/projects/android-library#AddDependency) lists all the steps to add the library to your own projects.


### Build the cpp runtimes 

If you have changed the cpp submodule, you will need to rebuild the cpp runtimes to generate the new .so files.

```bash
cd cpp 

./build.rive.for.sh -c -a x86
./build.rive.for.sh -c -a x86_64
./build.rive.for.sh -c -a arm64-v8a
./build.rive.for.sh -c -a armeabi-v7a
```