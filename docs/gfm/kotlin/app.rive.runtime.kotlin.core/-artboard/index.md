//[kotlin](../../../index.md)/[app.rive.runtime.kotlin.core](../index.md)/[Artboard](index.md)



# Artboard  
 [androidJvm] class [Artboard](index.md)(**cppPointer**: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))

[Artboard](index.md)s as designed in the Rive animation editor.



This object has a counterpart in c++, which implements a lot of functionality. The [cppPointer](cpp-pointer.md) keeps track of this relationship.



[Artboard](index.md)s provide access to available [Animation](../-animation/index.md)s, and some basic properties. You can [draw](draw.md) artboards using a [Renderer](../-renderer/index.md) that is tied to a canvas.



The constructor uses a [cppPointer](cpp-pointer.md) to point to its c++ counterpart object.

   


## Constructors  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin.core/Artboard/Artboard/#kotlin.Long/PointingToDeclaration/"></a>[Artboard](-artboard.md)| <a name="app.rive.runtime.kotlin.core/Artboard/Artboard/#kotlin.Long/PointingToDeclaration/"></a> [androidJvm] fun [Artboard](-artboard.md)(cppPointer: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))   <br>|


## Functions  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/Artboard/advance/#kotlin.Float/PointingToDeclaration/"></a>[advance](advance.md)| <a name="app.rive.runtime.kotlin.core/Artboard/advance/#kotlin.Float/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [advance](advance.md)(elapsedTime: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html))  <br>More info  <br>Advancing the artboard updates the layout for all dirty components contained in the [Artboard](index.md) updates the positions forces all components in the [Artboard](index.md) to be laid out.  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/animation/#kotlin.Int/PointingToDeclaration/"></a>[animation](animation.md)| <a name="app.rive.runtime.kotlin.core/Artboard/animation/#kotlin.Int/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [animation](animation.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [Animation](../-animation/index.md)  <br>More info  <br>Get the animation at a given [index](animation.md) in the [Artboard](index.md).  <br><br><br>[androidJvm]  <br>Content  <br>fun [animation](animation.md)(name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Animation](../-animation/index.md)  <br>More info  <br>Get the animation with a given [name](animation.md) in the [Artboard](index.md).  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/draw/#app.rive.runtime.kotlin.core.Renderer/PointingToDeclaration/"></a>[draw](draw.md)| <a name="app.rive.runtime.kotlin.core/Artboard/draw/#app.rive.runtime.kotlin.core.Renderer/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [draw](draw.md)(renderer: [Renderer](../-renderer/index.md))  <br>More info  <br>Draw the the artboard to the [renderer](draw.md).  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/getInstance/#/PointingToDeclaration/"></a>[getInstance](get-instance.md)| <a name="app.rive.runtime.kotlin.core/Artboard/getInstance/#/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [getInstance](get-instance.md)(): [Artboard](index.md)  <br>More info  <br>Get a cloned Artboard of the current artboard as it stands.  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/stateMachine/#kotlin.Int/PointingToDeclaration/"></a>[stateMachine](state-machine.md)| <a name="app.rive.runtime.kotlin.core/Artboard/stateMachine/#kotlin.Int/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [stateMachine](state-machine.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [StateMachine](../-state-machine/index.md)  <br>More info  <br>Get the animation at a given [index](state-machine.md) in the [Artboard](index.md).  <br><br><br>[androidJvm]  <br>Content  <br>fun [stateMachine](state-machine.md)(name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [StateMachine](../-state-machine/index.md)  <br>More info  <br>Get the animation with a given [name](state-machine.md) in the [Artboard](index.md).  <br><br><br>|


## Properties  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/Artboard/animationCount/#/PointingToDeclaration/"></a>[animationCount](animation-count.md)| <a name="app.rive.runtime.kotlin.core/Artboard/animationCount/#/PointingToDeclaration/"></a> [androidJvm] val [animationCount](animation-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Get the number of animations stored inside the [Artboard](index.md).   <br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/animationNames/#/PointingToDeclaration/"></a>[animationNames](animation-names.md)| <a name="app.rive.runtime.kotlin.core/Artboard/animationNames/#/PointingToDeclaration/"></a> [androidJvm] val [animationNames](animation-names.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)<[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)>Get the names of the animations in the artboard.   <br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/bounds/#/PointingToDeclaration/"></a>[bounds](bounds.md)| <a name="app.rive.runtime.kotlin.core/Artboard/bounds/#/PointingToDeclaration/"></a> [androidJvm] val [bounds](bounds.md): [AABB](../-a-a-b-b/index.md)Get the bounds of Artboard as defined in the rive editor.   <br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/cppPointer/#/PointingToDeclaration/"></a>[cppPointer](cpp-pointer.md)| <a name="app.rive.runtime.kotlin.core/Artboard/cppPointer/#/PointingToDeclaration/"></a> [androidJvm] val [cppPointer](cpp-pointer.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)   <br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/firstAnimation/#/PointingToDeclaration/"></a>[firstAnimation](first-animation.md)| <a name="app.rive.runtime.kotlin.core/Artboard/firstAnimation/#/PointingToDeclaration/"></a> [androidJvm] val [firstAnimation](first-animation.md): [Animation](../-animation/index.md)Get the first [Animation](../-animation/index.md) of the [Artboard](index.md).   <br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/firstStateMachine/#/PointingToDeclaration/"></a>[firstStateMachine](first-state-machine.md)| <a name="app.rive.runtime.kotlin.core/Artboard/firstStateMachine/#/PointingToDeclaration/"></a> [androidJvm] val [firstStateMachine](first-state-machine.md): [StateMachine](../-state-machine/index.md)Get the first [StateMachine](../-state-machine/index.md) of the [Artboard](index.md).   <br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/name/#/PointingToDeclaration/"></a>[name](name.md)| <a name="app.rive.runtime.kotlin.core/Artboard/name/#/PointingToDeclaration/"></a> [androidJvm] val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)Get the [name](name.md) of the Artboard.   <br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/stateMachineCount/#/PointingToDeclaration/"></a>[stateMachineCount](state-machine-count.md)| <a name="app.rive.runtime.kotlin.core/Artboard/stateMachineCount/#/PointingToDeclaration/"></a> [androidJvm] val [stateMachineCount](state-machine-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Get the number of state machines stored inside the [Artboard](index.md).   <br>|
| <a name="app.rive.runtime.kotlin.core/Artboard/stateMachineNames/#/PointingToDeclaration/"></a>[stateMachineNames](state-machine-names.md)| <a name="app.rive.runtime.kotlin.core/Artboard/stateMachineNames/#/PointingToDeclaration/"></a> [androidJvm] val [stateMachineNames](state-machine-names.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)<[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)>Get the names of the stateMachines in the artboard.   <br>|

