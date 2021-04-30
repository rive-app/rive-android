# Rive-Android -- Rive's Android runtime

## Create and ship interactive animations to any platform

[Rive](https://rive.app/) is a real-time interactive design and animation tool. Use our collaborative editor to create motion graphics that respond to different states and user inputs. Then load your animations into apps, games, and websites with our lightweight open-source runtimes.

## Beta Release

This is the Android runtime for [Rive](https://rive.app), currently in beta. The api is subject to change as we continue to improve it. Please file issues and PRs for anything busted, missing, or just wrong.

## Installing

To add Rive in your project, include the following in your `dependencies`:

```
implementation 'app.rive:rive-android:0.0.9'
```

## RiveAnimationView

The simplest way to get a rive animation into your application is to include it as part of a layout. The following will include the rive file loaded from the raw resources location, and auto play its first animation.

``` xml

<app.rive.runtime.kotlin.RiveAnimationView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:riveResource="@raw/off_road_car_blog" />
```

## Layout

The animation view can be further customized as part of specifying [layout attributes](https://github.com/rive-app/rive-android/blob/master/kotlin/src/main/res/values/attrs.xml).

`fit` can be specified to determine how the animation should be resized to fit its container. The available choices are `FILL` , `CONTAIN` , `COVER` , `FIT_WIDTH` , `FIT_HEIGHT` , `NONE` , `SCALE_DOWN`

`alignment` informs how it should be aligned within the container. The available choices are `TOP_LEFT` , `TOP_CENTER` , `TOP_RIGHT` , `CENTER_LEFT` , `CENTER` , `CENTER_RIGHT` , `BOTTOM_LEFT` , `BOTTOM_CENTER` , `BOTTOM_RIGHT` .

``` xml

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

## Playback controls

Animations can be controlled in many ways, by default loading a RiveAnimationView with a resource file will autoplay the first animation on the first artboard. The artboard and animation can be specified.

``` xml

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

``` kotlin

animationView.setRiveResource(
    R.raw.artboard_animations,
    artboardName = "Square", 
    animationName = "rollaround", 
    autoplay = true
)
```

furthermore animations can be controlled later too:

To play an animation named rollaround.

``` kotlin

animationView.play("rollaround")
```

multiple animations can play at the same time, and additional animations can be added at any time

``` kotlin

animationView.play(listOf("bouncing", "windshield_wipers"))
```

When playing animations, the Loop Mode and direction of the animations can also be set per animation.

``` kotlin

animationView.play(listOf("bouncing", "windshield_wipers"), Loop.ONE_SHOT, Direction.Backwards)
```

Similarly animations can be paused, or stopped, either all at the same time, or one by one.

``` kotlin

animationView.stop()
animationView.stop("bouncing")
animationView.stop(listOf("bouncing", "windshield_wipers"))
```

``` kotlin

animationView.pause()
animationView.pause("bouncing")
animationView.pause(listOf("bouncing", "windshield_wipers"))
```

### Mixing

Mixing goes further than just playing multiple animations at the same time, animations can use a mix factor between 0 and 1, to allow multiple animations effects to blend together. The high level views do not expose this currently. but you can wrap your own render loop around the core libraries. The advance function is where you can specify a mix factor

## Events

The rive android runtimes allow listener registration, take a look at the events section in the [rive player](https://github.com/rive-app/rive-android/blob/master/app/src/main/java/app/rive/runtime/example/AndroidPlayerActivity.kt) for an example of how this works.

``` kotlin

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

## Manually Initializing Rive

Rive needs to initialize its runtime when your app starts. It includes an [initializer](https://developer.android.com/topic/libraries/app-startup) that does this for you automatically. The initialization provider is set up in the Rive module's manifest.

Gradle will typically merge up the module's manifest with your app's so you don't have to do anything. If you're using another build system, this might not happen.

If that's the case, you can activate this by either including a provider in your manifest file:

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

or calling the initializer in your code:

```kotlin
AppInitializer.getInstance(applicationContext)
  .initializeComponent(RiveInitializer::class.java)
```

You'll need the add a dependency for Jetpack Startup:

```groovy
dependencies {
    implementation "androidx.startup:startup-runtime:1.0.0"
}
```
