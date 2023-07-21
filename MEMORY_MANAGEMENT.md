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

The `RiveAnimationView` abstracts some of this away from the user. The `View` allocates Native objects
and de-allocates them through the JNI during
its lifecycle, using Android's hooks `onAttachedToWindow()` and `onDetachedFromWindow()`.

`RiveAnimationView` manages all of this internally: when a `View` is detached from the window, it will also delete its corresponding
Rive `File` object, as well as any classes derived from that `File` object, such as `Artboard`
or `StateMachineInstance`s.

Users will not have to delete the C++ explicitly objects when
using `RiveAnimationView`. However, the runtime will throw a `RiveException` when trying to access C++ properties of a deleted object. When encountering these exceptions, it
indicates that the object throwing them is stale and must be replaced.

So, avoid using Native Rive objects outside the lifecycle of the `View` that created them - after `RiveAnimationView.onDetachedFromWindow()`.

The diagram below illustrates the class hierarchy for `RiveAnimationView` highlighting only a few important fields.

```
         +------------------------------+
         |RiveAnimationView             |
         +------------------------------+
         |override                      |
         |renderer: RiveArtboardRenderer|
         +-------------+----------------+
                       |
                     is-a
                       |
            +----------v-----------+
            |RiveTextureView       |
            +----------------------+
            |abstract              |
            |renderer: Renderer    |
            +----------+-----------+
                       |
                       |
             +---------+----------------+
             |                          |
           is-a                    implements
             |                          |
+------------v-------+    +-------------v-------------+
|TextureView         |    |SurfaceTextureListener     |
+--------------------+    +---------------------------+
|onAttachedToWindow  |    |onSurfaceTextureAvailable  |
|onDetachedFromWindow|    |onSurfaceTextureDestroyed  |
|onVisibilityChanged |    |onSurfaceTextureUpdated    |
+--------------------+    |onSurfaceTextureSizeChanged|
                          +---------------------------+
```

### `NativeObjects`, Inheritance, and dependencies

Whenever there is a `has` dependency, the object that is created is "owned" by its creator. 
<br />The `is-a` relationship indicates class inheritance.

For example, when a `File` instances an `Artboard` it will add that instance to its `dependencies`. 
When a Native object calls `release()`, it will first release its reference on all its dependents and
then it will decrease its own reference counter - if the counter reaches 0 then it can be disposed of
and it'll call its JNI destructor.

The diagram below illustrates the internal class hierarchy for the `Renderer`, as well as its `NativeObject` dependencies. 
That is, a `RiveArtboardRenderer` only "owns" a `File`. When `RiveArtboardRenderer` calls `release()`, it'll release of the `File` only,
which in turn will cascade the `release()` call on its own dependents.

```
                                         dependencies: List<NativeObject>
                                                         |
                                                         |
                                                        has
                                                         |
+--------------------+       +------------+       +------+-----+
|RiveArtboardRenderer+-is-a->|  Renderer  +-is-a->|NativeObject|
+---------+----------+       +------------+       +------^-----+
          |                                              |
         has                                             |
          |                                              |
        +-+--+                                           |
        |File+--------------------------------------is-a-+
        +-+--+                                           |
          |                                              |
         has                                             |
          |                                              |
      +---+----+                                         |
      |Artboard+------------------------------------is-a-+
      +---+----+                                         |
          |                                              |
         has                                             |
          |                                              |
          |                                              |
          | +-----------------------+                    |
          +-+LinearAnimationInstance+---------------is-a-+
          | +-----------------------+                    |
          |                                              |
          | +--------------------+                       |
          +-+StateMachineInstance+------------------is-a-+
            +---------+----------+                       |
                      |                                  |
                     has                                 |
                      |                                  |
                 +----+-----+                            |
                 |SMIInput  +-----------------------is-a-+
                 |LayerState|
                 +----------+
```

## Custom Render Loop

Our [`LowLevelActivity`](./app/src/main/java/app/rive/runtime/example/LowLevelActivity.kt) example
shows how a user can do that manually by adding the `File` onto the `Renderer`'s dependencies, which
will do the cleanup at the right time, that is within `onDetachedFromWindow`.

### Alternatives

### Relying on `finalize()`

Pre rive-android 4.0 we relied on `finalize()` for memory management. On paper Kotlin's finalize
should be exactly what we need to avoid some of the more manual aspects and gotchas introduced with
the above memory management scenario. This was our initial approach to memory management,
unfortunately it's been shown to produce occasional errors with older android devices. We seem to be
running into something along 
[these lines](https://stackoverflow.com/questions/24021609/how-to-handle-java-util-concurrent-timeoutexception-android-os-binderproxy-fin): 
. Furthermore, `finalize()` is deprecated.
