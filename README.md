![Build Status](https://github.com/rive-app/rive-android/actions/workflows/release.yml/badge.svg)
![Test Status](https://github.com/rive-app/rive-android/actions/workflows/tests.yml/badge.svg)
![Discord badge](https://img.shields.io/discord/532365473602600965)
![Twitter handle](https://img.shields.io/twitter/follow/rive_app.svg?style=social&label=Follow)

# rive-android

Android runtime for [Rive](https://rive.app/)

Further runtime documentation can be found in [Rive's help center](https://help.rive.app/runtimes).

## Create and ship interactive animations to any platform

[Rive](https://rive.app/) is a real-time interactive design and animation tool. Use our collaborative editor to create motion graphics that respond to different states and user inputs. Then load your animations into apps, games, and websites with our lightweight open-source runtimes.

## Beta Release

This is the Android runtime for [Rive](https://rive.app), currently in beta. The api is subject to change as we continue to improve it. Please file issues and PRs for anything busted, missing, or just wrong.

### Version 2 Release Notes

This update introduces a new setup for managing your own render loop.
<br />
`RiveDrawable` has now been renamed [RiveArtboardRenderer](kotlin/src/main/java/app/rive/runtime/kotlin/RiveArtboardRenderer.kt) and it is no longer an Android `Drawable`. An example on how to drive your own loop is still available in [LowLevelActivity.kt](app/src/main/java/app/rive/runtime/example/LowLevelActivity.kt).

[RiveAnimationView](kotlin/src/main/java/app/rive/runtime/kotlin/RiveAnimationView.kt) has the same API as the previous version and will still work as before.

## Installing

To add Rive in your project, include the following in your `dependencies` :

```
implementation 'app.rive:rive-android:x.x.x'
```

## Initializing Rive

Rive needs to initialize its runtime when your app starts.

It can be done via an [initializer](https://developer.android.com/topic/libraries/app-startup) that does this for you automatically. The initialization provider can be set up directly in your app's manifest file:

```xml
<provider
  android:name="androidx.startup.InitializationProvider"
  android:authorities="${applicationId}.androidx-startup"
  android:exported="false"
  tools:node="merge">
    <meta-data android:name="app.rive.runtime.kotlin.RiveInitializer"
      android:value="androidx.startup" />
</provider>
```

Otherwise this can be achieved by calling the initializer in your code:

```kotlin
AppInitializer.getInstance(applicationContext)
  .initializeComponent(RiveInitializer::class.java)
```

You'll need the add a dependency for Jetpack Startup:

```groovy
dependencies {
    implementation "androidx.startup:startup-runtime:1.1.0"
}
```

If you want to initialize Rive yourself, this can also be done in code:

```kotlin
Rive.init(context)
```

## RiveAnimationView

The simplest way to get a rive animation into your application is to include it as part of a layout. The following will include the rive file loaded from the raw resources location, and auto play its first animation.

```xml

<app.rive.runtime.kotlin.RiveAnimationView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:riveResource="@raw/off_road_car_blog" />
```

Rive uses C++ for its setup and memory initialization. `RiveAnimationView` allocates Native objects and deallocates them through the JNI and makes sure that they are removed from memory during the `View` lifecycle, using Android's hooks `onAttachedToWindow()` and `onDetachedFromWindow()`.

When using `File` objects and grabbing a reference to an `Artboard` this'll internally allocate `Artboard` objects. In turn, `Artboard` objects internally allocate `AnimationInstance`s and/or `StateMachineInstance`s. <br />
This creates a DAG of dependencies that is resolved internally by the objects. Once the `File` is queued for deletion, it'll cascade onto its dependents - which will do the same.

`RiveAnimationView` manages all of this internally, by allocating and deallocating `File` for the user.

Our [`LowLevelActivity`](./app/src/main/java/app/rive/runtime/example/LowLevelActivity.kt) example shows how a user can do that manually by adding the `File` onto the `Renderer`'s dependencies, which will do the cleanup at the right time, that is within `onDetachedFromWindow`.

## Layout

The animation view can be further customized as part of specifying [layout attributes](https://github.com/rive-app/rive-android/blob/master/kotlin/src/main/res/values/attrs.xml).

`fit` can be specified to determine how the animation should be resized to fit its container. The available choices are `FILL` , `CONTAIN` , `COVER` , `FIT_WIDTH` , `FIT_HEIGHT` , `NONE` , `SCALE_DOWN`

`alignment` informs how it should be aligned within the container. The available choices are `TOP_LEFT` , `TOP_CENTER` , `TOP_RIGHT` , `CENTER_LEFT` , `CENTER` , `CENTER_RIGHT` , `BOTTOM_LEFT` , `BOTTOM_CENTER` , `BOTTOM_RIGHT` .

```xml

<app.rive.runtime.kotlin.RiveAnimationView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:riveResource="@raw/off_road_car_blog"
        app:riveAlignment="CENTER"
        app:riveFit="CONTAIN"
        />
```

Or

```kotlin
animationView.fit = Fit.FILL
animationView.alignment = Alignment.CENTER
```

## Playback controls

Animations can be controlled in many ways, by default loading a RiveAnimationView with a resource file will autoplay the first animation on the first artboard. The artboard and animation can be specified.

```xml

<app.rive.runtime.kotlin.RiveAnimationView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:adjustViewBounds="true"
                app:riveAutoPlay="true"
                app:riveArtboard="Square"
                app:riveAnimation="rollaround"
                app:riveResource="@raw/artboard_animations" />
```

Or

```kotlin

animationView.setRiveResource(
    R.raw.artboard_animations,
    artboardName = "Square",
    animationName = "rollaround",
    autoplay = true
)
```

furthermore animations can be controlled later too:

To play an animation named rollaround.

```kotlin

animationView.play("rollaround")
```

multiple animations can play at the same time, and additional animations can be added at any time

```kotlin

animationView.play(listOf("bouncing", "windshield_wipers"))
```

When playing animations, the Loop Mode and direction of the animations can also be set per animation.

```kotlin

animationView.play(listOf("bouncing", "windshield_wipers"), Loop.ONE_SHOT, Direction.Backwards)
```

Similarly animations can be paused, or stopped, either all at the same time, or one by one.

```kotlin

animationView.stop()
animationView.stop("bouncing")
animationView.stop(listOf("bouncing", "windshield_wipers"))
```

```kotlin

animationView.pause()
animationView.pause("bouncing")
animationView.pause(listOf("bouncing", "windshield_wipers"))
```

### Mixing

Mixing goes further than just playing multiple animations at the same time, animations can use a mix factor between 0 and 1, to allow multiple animations effects to blend together. The high level views do not expose this currently. but you can wrap your own render loop around the core libraries. The advance function is where you can specify a mix factor

## Events

The rive android runtimes allow listener registration, take a look at the events section in the [rive player](https://github.com/rive-app/rive-android/blob/master/app/src/main/java/app/rive/runtime/example/AndroidPlayerActivity.kt) for an example of how this works.

```kotlin

findViewById<RiveAnimationView>(R.id.android_player_view)
val listener = object : Listener {
    override fun notifyPlay(animation: LinearAnimationInstance) {
        // Do something
    }

    override fun notifyPause(animation: LinearAnimationInstance) {
        // Do something
    }

    override fun notifyStop(animation: LinearAnimationInstance) {
        // Do something
    }

    override fun notifyLoop(animation: LinearAnimationInstance) {
        // Do something
    }
}
animationView.registerListener(listener)
```

### F. A. Q.

#### Does animation play order matter?

Yes, animations are applied in order. Animations animate a property of a shape from one position to another.
If multiple animations are playing that are setting the same property on the same shape, only the last applied change will be visible.

The way past this and into some pretty cool effects will take you to mixing, where multiple animations are applied partially. `RiveAnimationView` does not provide options to set mixing values though, so to take advantage of this, you will need to run your own render loop. You can still use the core parts of this library to interact with Rive files though!

## Project Layout

### `/kotlin`

This is the main module of our android library, you can find a useful `RiveAnimationView` or `RiveArtboardRenderer` in the `app.rive.runtime.kotlin` namespace.
The underlying [C++ runtimes](https://github.com/rive-app/rive-cpp) is mapped to objects in the `app.rive.runtime.kotlin.core` namespace. These allow more fine grained control for more complex animation loops. Our high level views are simply built on top of this.

### `/app`

Multiple sample activities can be found here, this can be a useful reference for getting started with using the runtimes.

### `/cpp` && `/submodules`

The runtimes are built on top of our [C++ runtimes](https://github.com/rive-app/rive-cpp). these are included as a submodule in `/submodules`. The `/cpp` folder contains the C++ side of our bindings into android.

## Contributing

### Run the sample app

If you need the prebuilt .so files, do the following:
```bash
cd cpp
./build.all.sh
cd ..
```

- In Android Studio, make sure you select "Project" in the upper-left corner, not "Android".
- Select "app" as your target in the middle popup-menu. This is a folder inside runtime_android..." : Project:runtime_android:app
- Now pick run (the triangle in the middle of the top window).

### Updating rive-cpp

The runtime here should be updated to point to the latest `rive-cpp` submodule when that repo has new commits merged in. This ensures the `rive-android` runtime is up-to-date with its underlying native code layer to pull in latest patches, features, and more. Follow the steps below to update this submodule:

1. Pull in the latest commmits from `rive-cpp`, at the root level:

```bash
git submodule update --recursive
# Or git submodule update --init --recursive if you just pulled down the project
cd submodules/rive-cpp
git checkout origin/master
cd ../..
```

2. At this point you should see a Git diff of the submodule pointing to the latest commit on `master` from `rive-cpp`.

```bash
git add .
```

3. The Android NDK builds `.so` files for [different architectures](https://developer.android.com/ndk/guides/abis). <br />
   The current NDK version we're using is stored in [.ndk_version](./cpp/.ndk_version) ([How to install a specific NDK version](https://developer.android.com/studio/projects/install-ndk#specific-version)) <br />
   We also need to rebuild new `.so` files (located in `/kotlin/src/main/jniLibs/`) when pulling in latest changes from `rive-cpp`:

```bash
cd cpp/
# Builds .so files for each architecture
# Note: You may need to install a few dependencies for this script to run

# Add NDK_PATH variable to your .zshenv
NDK_VERSION=$(tr <.ndk_version -d " \t\n\r")
echo 'export NDK_PATH=~/Library/Android/sdk/ndk/${NDK_VERSION}' >> ~/.zshenv
source ~/.zshenv

# Ninja - brew install ninja
# Premake5 - Need to add to your path
./build.all.sh
# After the script above completes successfully, you should see 4 new .so files, so let's add them
git add .
```

4. Run the test suite

- open in Android Studio
- select Project, not Android, in the upper-left corner
- right-click on kotlin/src/androidTest
- "Run 'All Tests'"

5. Commit the submodule updates / new `.so` files to a branch, and submit a PR to `master`

### Updating Dokka docs

To update the documentation, run the `rive-android:kotlin [dokkaGfm]` task.
And then replace the contents of docs with the newly generated output

```sh
rm -rf docs/gfm
mv kotlin/build/dokka/gfm docs
```

(autogenerated) API documentation can be found [here](https://github.com/rive-app/rive-android/blob/documentationAttempt/docs/gfm/index.md)
