//[kotlin](../../../index.md)/[app.rive.runtime.kotlin.core](../index.md)/[StateMachine](index.md)



# StateMachine  
 [androidJvm] class [StateMachine](index.md)(**cppPointer**: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))

[StateMachine](index.md)s as designed in the Rive animation editor.



This object has a counterpart in c++, which implements a lot of functionality. The [cppPointer](cpp-pointer.md) keeps track of this relationship.



These can be used with [StateMachineInstance](../-state-machine-instance/index.md)s and [Artboard](../-artboard/index.md)s to draw frames



The constructor uses a [cppPointer](cpp-pointer.md) to point to its c++ counterpart object.

   


## Constructors  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin.core/StateMachine/StateMachine/#kotlin.Long/PointingToDeclaration/"></a>[StateMachine](-state-machine.md)| <a name="app.rive.runtime.kotlin.core/StateMachine/StateMachine/#kotlin.Long/PointingToDeclaration/"></a> [androidJvm] fun [StateMachine](-state-machine.md)(cppPointer: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))   <br>|


## Functions  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/StateMachine/_convertInput/#app.rive.runtime.kotlin.core.StateMachineInput/PointingToDeclaration/"></a>[_convertInput](_convert-input.md)| <a name="app.rive.runtime.kotlin.core/StateMachine/_convertInput/#app.rive.runtime.kotlin.core.StateMachineInput/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [_convertInput](_convert-input.md)(input: [StateMachineInput](../-state-machine-input/index.md)): [StateMachineInput](../-state-machine-input/index.md)  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/StateMachine/input/#kotlin.Int/PointingToDeclaration/"></a>[input](input.md)| <a name="app.rive.runtime.kotlin.core/StateMachine/input/#kotlin.Int/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [input](input.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [StateMachineInput](../-state-machine-input/index.md)  <br>More info  <br>Get the animation at a given [index](input.md) in the [Artboard](../-artboard/index.md).  <br><br><br>[androidJvm]  <br>Content  <br>fun [input](input.md)(name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [StateMachineInput](../-state-machine-input/index.md)  <br>More info  <br>Get the animation with a given [name](input.md) in the [Artboard](../-artboard/index.md).  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/StateMachine/toString/#/PointingToDeclaration/"></a>[toString](to-string.md)| <a name="app.rive.runtime.kotlin.core/StateMachine/toString/#/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>open override fun [toString](to-string.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br><br><br>|


## Properties  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/StateMachine/cppPointer/#/PointingToDeclaration/"></a>[cppPointer](cpp-pointer.md)| <a name="app.rive.runtime.kotlin.core/StateMachine/cppPointer/#/PointingToDeclaration/"></a> [androidJvm] val [cppPointer](cpp-pointer.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachine/inputCount/#/PointingToDeclaration/"></a>[inputCount](input-count.md)| <a name="app.rive.runtime.kotlin.core/StateMachine/inputCount/#/PointingToDeclaration/"></a> [androidJvm] val [inputCount](input-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Return the number of inputs configured for the state machine.   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachine/inputNames/#/PointingToDeclaration/"></a>[inputNames](input-names.md)| <a name="app.rive.runtime.kotlin.core/StateMachine/inputNames/#/PointingToDeclaration/"></a> [androidJvm] val [inputNames](input-names.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)<[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)>Get the names of the stateMachineInputs in the state machine.   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachine/inputs/#/PointingToDeclaration/"></a>[inputs](inputs.md)| <a name="app.rive.runtime.kotlin.core/StateMachine/inputs/#/PointingToDeclaration/"></a> [androidJvm] val [inputs](inputs.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)<[StateMachineInput](../-state-machine-input/index.md)>Get the stateMachineInputs in the state machine.   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachine/layerCount/#/PointingToDeclaration/"></a>[layerCount](layer-count.md)| <a name="app.rive.runtime.kotlin.core/StateMachine/layerCount/#/PointingToDeclaration/"></a> [androidJvm] val [layerCount](layer-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Return the number of layers configured for the state machine.   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachine/name/#/PointingToDeclaration/"></a>[name](name.md)| <a name="app.rive.runtime.kotlin.core/StateMachine/name/#/PointingToDeclaration/"></a> [androidJvm] val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)Return the name given to an animation   <br>|

