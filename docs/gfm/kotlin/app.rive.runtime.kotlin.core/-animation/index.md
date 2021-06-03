//[kotlin](../../../index.md)/[app.rive.runtime.kotlin.core](../index.md)/[Animation](index.md)



# Animation  
 [androidJvm] class [Animation](index.md)(**cppPointer**: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))

[Animation](index.md)s as designed in the Rive animation editor.



This object has a counterpart in c++, which implements a lot of functionality. The [cppPointer](cpp-pointer.md) keeps track of this relationship.



These can be used with [LinearAnimationInstance](../-linear-animation-instance/index.md)s and [Artboard](../-artboard/index.md)s to draw frames



The constructor uses a [cppPointer](cpp-pointer.md) to point to its c++ counterpart object.

   


## Constructors  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin.core/Animation/Animation/#kotlin.Long/PointingToDeclaration/"></a>[Animation](-animation.md)| <a name="app.rive.runtime.kotlin.core/Animation/Animation/#kotlin.Long/PointingToDeclaration/"></a> [androidJvm] fun [Animation](-animation.md)(cppPointer: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))   <br>|


## Functions  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/Animation/toString/#/PointingToDeclaration/"></a>[toString](to-string.md)| <a name="app.rive.runtime.kotlin.core/Animation/toString/#/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>open override fun [toString](to-string.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br><br><br>|


## Properties  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/Animation/cppPointer/#/PointingToDeclaration/"></a>[cppPointer](cpp-pointer.md)| <a name="app.rive.runtime.kotlin.core/Animation/cppPointer/#/PointingToDeclaration/"></a> [androidJvm] val [cppPointer](cpp-pointer.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)   <br>|
| <a name="app.rive.runtime.kotlin.core/Animation/duration/#/PointingToDeclaration/"></a>[duration](duration.md)| <a name="app.rive.runtime.kotlin.core/Animation/duration/#/PointingToDeclaration/"></a> [androidJvm] val [duration](duration.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Get the duration of an animation in frames, this does not take [workStart](work-start.md) and [workEnd](work-end.md) into account   <br>|
| <a name="app.rive.runtime.kotlin.core/Animation/effectiveDuration/#/PointingToDeclaration/"></a>[effectiveDuration](effective-duration.md)| <a name="app.rive.runtime.kotlin.core/Animation/effectiveDuration/#/PointingToDeclaration/"></a> [androidJvm] val [effectiveDuration](effective-duration.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Get the duration of an animation in frames, taking [workStart](work-start.md) and [workEnd](work-end.md) into account   <br>|
| <a name="app.rive.runtime.kotlin.core/Animation/effectiveDurationInSeconds/#/PointingToDeclaration/"></a>[effectiveDurationInSeconds](effective-duration-in-seconds.md)| <a name="app.rive.runtime.kotlin.core/Animation/effectiveDurationInSeconds/#/PointingToDeclaration/"></a> [androidJvm] val [effectiveDurationInSeconds](effective-duration-in-seconds.md): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html)Get the duration of an animation in seconds, taking [workStart](work-start.md) and [workEnd](work-end.md) into account   <br>|
| <a name="app.rive.runtime.kotlin.core/Animation/endTime/#/PointingToDeclaration/"></a>[endTime](end-time.md)| <a name="app.rive.runtime.kotlin.core/Animation/endTime/#/PointingToDeclaration/"></a> [androidJvm] val [endTime](end-time.md): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html)Return the offset in frames to the end of an animation.   <br>|
| <a name="app.rive.runtime.kotlin.core/Animation/fps/#/PointingToDeclaration/"></a>[fps](fps.md)| <a name="app.rive.runtime.kotlin.core/Animation/fps/#/PointingToDeclaration/"></a> [androidJvm] val [fps](fps.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Return the fps configured for the animation   <br>|
| <a name="app.rive.runtime.kotlin.core/Animation/loop/#/PointingToDeclaration/"></a>[loop](loop.md)| <a name="app.rive.runtime.kotlin.core/Animation/loop/#/PointingToDeclaration/"></a> [androidJvm] val [loop](loop.md): [Loop](../-loop/index.md)Configure the [Loop](../-loop/index.md) mode configured against an animation.   <br>|
| <a name="app.rive.runtime.kotlin.core/Animation/name/#/PointingToDeclaration/"></a>[name](name.md)| <a name="app.rive.runtime.kotlin.core/Animation/name/#/PointingToDeclaration/"></a> [androidJvm] val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)Return the name given to an animation   <br>|
| <a name="app.rive.runtime.kotlin.core/Animation/startTime/#/PointingToDeclaration/"></a>[startTime](start-time.md)| <a name="app.rive.runtime.kotlin.core/Animation/startTime/#/PointingToDeclaration/"></a> [androidJvm] val [startTime](start-time.md): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html)Return the offset in frames to the beginning of an animations.   <br>|
| <a name="app.rive.runtime.kotlin.core/Animation/workEnd/#/PointingToDeclaration/"></a>[workEnd](work-end.md)| <a name="app.rive.runtime.kotlin.core/Animation/workEnd/#/PointingToDeclaration/"></a> [androidJvm] val [workEnd](work-end.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Return the offset in frames to the end of an animations work area.   <br>|
| <a name="app.rive.runtime.kotlin.core/Animation/workStart/#/PointingToDeclaration/"></a>[workStart](work-start.md)| <a name="app.rive.runtime.kotlin.core/Animation/workStart/#/PointingToDeclaration/"></a> [androidJvm] val [workStart](work-start.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Return the offset in frames to the beginning of an animations work area.   <br>|

