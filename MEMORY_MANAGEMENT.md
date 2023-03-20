# Memory Management

Rive uses C++ to help share code across our various runtimes while offering high fidelity between
the different platforms. The bulk of the runtime logic resides in our C++ layers, while our native
runtimes simply wrap key calls. Most of this interaction is transparent to library users, however
there are some things to be aware of as your use becomes more advanced.

Many of our classes store references to their cpp companion; this includes but is not limited
to `File`, `Artboard`, `LinearAnimationInstance`, `StateMachineInstance`, `SMIInputs`... When we
invoke some methods on these classes, we are actually passing calls through the jni to our C++
objects.

Most objects that are created in C++ are also garbage collected in C++, but this is not the case
for `File`, `Artboard`, `LinearAnimationInstance` & `StateMachineInstance`. We need to manually call
delete for these objects, otherwise we will leak memory.

## RiveAnimationView

The `RiveAnimationView` abstracts some of this away from the user. The view allocates Native objects
and de-allocates them through the JNI and makes sure that they are removed from memory during
the `View` lifecycle, using Android's hooks `onAttachedToWindow()` and `onDetachedFromWindow()`.

`RiveAnimationView` manages all of this internally. When a view is detached from the window, the
Rive `File` object is deleted, as well any classes derived from the object such as `Artboard`
& `StateMachineInstance`s.

This means the user will not have to explicitly delete the C++ objects themselves when
using `RiveAnimationView`. However the runtime will throw a `RiveException` when an object that has
already has its C++ counterpart deleted is accessed. Should you encounter these exceptions it
indicates that the object throwing these exceptions is stale and needs to be replaced.

This means it is still important to avoid using rive objects outside the life cycle of the view they
were created in.

## Custom Render Loop

Our [`LowLevelActivity`](./app/src/main/java/app/rive/runtime/example/LowLevelActivity.kt) example
shows how a user can do that manually by adding the `File` onto the `Renderer`'s dependencies, which
will do the cleanup at the right time, that is within `onDetachedFromWindow`.

### Alternatives

### Relying on `finalize()`

Pre rive-android 4.0 we relied on finalize() for memory management. On paper Kotlin's finalize
should be exactly what we need to avoid some of the more manual aspects and gotchas introduced with
the above memory management scenario. This was our initial approach to memory management,
unfortunately it's been shown to produce occasional errors with older android devices. We seem to be
running into something along these
lines: https://stackoverflow.com/questions/24021609/how-to-handle-java-util-concurrent-timeoutexception-android-os-binderproxy-fin
. Furthermore, `finalize()` is deprecated.
