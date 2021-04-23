# Rive-Android -- Rive's Android runtime

## Create and ship interactive animations to any platform

[Rive](https://rive.app/) is a real-time interactive design and animation tool. Use our collaborative editor to create motion graphics that respond to different states and user inputs. Then load your animations into apps, games, and websites with our lightweight open-source runtimes. 

## Beta Release

This is the Android runtime for [Rive](https://rive.app), currently in beta. The api is subject to change as we continue to improve it. Please file issues and PRs for anything busted, missing, or just wrong.

We are not currently available on Maven, so to use your own `.aar`

## Installing

1. Clone the repo `git clone --recurse-submodules git@github.com:rive-app/rive-android.git`
2. Open the directory as an Android Studio project
3. Build the `.aar`, with the Android Studio project open, in the menu select ** `Build` > `Make Module 'kotlin'` **.

    Android Studio will put the `.aar` library file in `kotlin/build/outputs/aar` .

4. [The documentation](https://developer.android.com/studio/projects/android-library#AddDependency) lists all the steps to add the library to your own projects.

## RiveAnimationView

The simplest way to get a rive animation into your application is to include it as part of a layout. The following will include the rive file loaded from the raw resources location, and auto play its first animation.

``` 

<app.rive.runtime.kotlin.RiveAnimationView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:riveResource="@raw/off_road_car_blog" />
```

## Layout

The animation view can be further customized as part of specifying [layout attributes](https://github.com/rive-app/rive-android/blob/master/kotlin/src/main/res/values/attrs.xml). 

`fit` can be specified to determine how the animation should be resized to fit its container. The available choices are `FILL` , `CONTAIN` , `COVER` , `FIT_WIDTH` , `FIT_HEIGHT` , `NONE` , `SCALE_DOWN`

`alignment` informs how it should be aligned within the container. The available choices are `TOP_LEFT` , `TOP_CENTER` , `TOP_RIGHT` , `CENTER_LEFT` , `CENTER` , `CENTER_RIGHT` , `BOTTOM_LEFT` , `BOTTOM_CENTER` , `BOTTOM_RIGHT` . 

``` 
<app.rive.runtime.kotlin.RiveAnimationView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:riveResource="@raw/off_road_car_blog" 
        app:riveAlignment="CENTER"
        app:riveFit="CONTAIN"
        />
```

Or 

``` kotlin
animationView.fit = Fit.FILL
animationView.alignment = Alignment.CENTER
```

## Project Layout

### /kotlin

This is the main module of the android library, you can find a useful `RiveAnimationView` or `RiveDrawable` in the `app.rive.runtime.kotlin` namespace.
The underlying [c++ runtimes](https://github.com/rive-app/rive-cpp) is mapped to objects in the `app.rive.runtime.kotlin.core` namespace. This can be used to allow for more fine grained control for more complex animation loops. Our high level views are simply built on top of this.

### /app

Multiple sample activities can be found here, this can be a useful reference for getting started with using the runtimes.

### /cpp && /submodules

The runtimes are built on top of our [c++ runtimes](https://github.com/rive-app/rive-cpp). these are included as a submodule in /submodules. The `/cpp` folder contains the c++ side of our bindings into android. 

#### Build the cpp runtimes

If you have changed the cpp submodule, or if you have made changes to the cpp bindings, you will need to rebuild the cpp runtimes to generate the new .so files.

``` bash
cd cpp 

./build.rive.for.sh -c -a x86
./build.rive.for.sh -c -a x86_64
./build.rive.for.sh -c -a arm64-v8a
./build.rive.for.sh -c -a armeabi-v7a
```
