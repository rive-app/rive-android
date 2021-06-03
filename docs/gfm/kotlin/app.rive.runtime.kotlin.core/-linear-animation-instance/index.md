//[kotlin](../../../index.md)/[app.rive.runtime.kotlin.core](../index.md)/[LinearAnimationInstance](index.md)



# LinearAnimationInstance  
 [androidJvm] class [LinearAnimationInstance](index.md)(**animation**: [Animation](../-animation/index.md)) : [PlayableInstance](../-playable-instance/index.md)

The [LinearAnimationInstance](index.md) is a helper to wrap common operations to play an [animation](animation.md).



This object has a counterpart in c++, which implements a lot of functionality. The  keeps track of this relationship.



Use this to keep track of an animation current state and progress. And to help [apply](apply.md) changes that the [animation](animation.md) makes to components in an [Artboard](../-artboard/index.md).

   


## Constructors  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/LinearAnimationInstance/#app.rive.runtime.kotlin.core.Animation/PointingToDeclaration/"></a>[LinearAnimationInstance](-linear-animation-instance.md)| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/LinearAnimationInstance/#app.rive.runtime.kotlin.core.Animation/PointingToDeclaration/"></a> [androidJvm] fun [LinearAnimationInstance](-linear-animation-instance.md)(animation: [Animation](../-animation/index.md))   <br>|


## Functions  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/advance/#kotlin.Float/PointingToDeclaration/"></a>[advance](advance.md)| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/advance/#kotlin.Float/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [advance](advance.md)(elapsedTime: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html)): [Loop](../-loop/index.md)?  <br>More info  <br>Advance the animation by the [elapsedTime](advance.md) in seconds.  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/apply/#app.rive.runtime.kotlin.core.Artboard#kotlin.Float/PointingToDeclaration/"></a>[apply](apply.md)| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/apply/#app.rive.runtime.kotlin.core.Artboard#kotlin.Float/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [apply](apply.md)(artboard: [Artboard](../-artboard/index.md), mix: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html) = 1.0f)  <br>More info  <br>Applies the animation instance's current set of transformations to an [artboard](apply.md).  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/time/#kotlin.Float/PointingToDeclaration/"></a>[time](time.md)| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/time/#kotlin.Float/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [time](time.md)(time: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html))  <br>More info  <br>Sets the animation's point in time to [time](time.md)  <br><br><br>|


## Properties  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/animation/#/PointingToDeclaration/"></a>[animation](animation.md)| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/animation/#/PointingToDeclaration/"></a> [androidJvm] val [animation](animation.md): [Animation](../-animation/index.md)   <br>|
| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/direction/#/PointingToDeclaration/"></a>[direction](direction.md)| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/direction/#/PointingToDeclaration/"></a> [androidJvm] var [direction](direction.md): [Direction](../-direction/index.md)Configure the [Direction](../-direction/index.md) of the animation instance [Direction.FORWARDS](../-direction/-f-o-r-w-a-r-d-s/index.md) or [Direction.BACKWARDS](../-direction/-b-a-c-k-w-a-r-d-s/index.md)   <br>|
| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/loop/#/PointingToDeclaration/"></a>[loop](loop.md)| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/loop/#/PointingToDeclaration/"></a> [androidJvm] var [loop](loop.md): [Loop](../-loop/index.md)Configure the [Loop](../-loop/index.md) mode configured against an animation.   <br>|
| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/time/#/PointingToDeclaration/"></a>[time](time.md)| <a name="app.rive.runtime.kotlin.core/LinearAnimationInstance/time/#/PointingToDeclaration/"></a> [androidJvm] val [time](time.md): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html)Returns the current point in time at which this instance has advance to.   <br>|

