//[kotlin](../../../index.md)/[app.rive.runtime.kotlin.core](../index.md)/[StateMachineInput](index.md)



# StateMachineInput  
 [androidJvm] open class [StateMachineInput](index.md)(**cppPointer**: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))

[StateMachineInput](index.md)s as designed in the Rive animation editor.



This object has a counterpart in c++, which implements a lot of functionality. The [cppPointer](cpp-pointer.md) keeps track of this relationship.



These can be used with [StateMachineInstance](../-state-machine-instance/index.md)s and [Artboard](../-artboard/index.md)s to draw frames



The constructor uses a [cppPointer](cpp-pointer.md) to point to its c++ counterpart object.

   


## Constructors  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin.core/StateMachineInput/StateMachineInput/#kotlin.Long/PointingToDeclaration/"></a>[StateMachineInput](-state-machine-input.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInput/StateMachineInput/#kotlin.Long/PointingToDeclaration/"></a> [androidJvm] fun [StateMachineInput](-state-machine-input.md)(cppPointer: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))   <br>|


## Functions  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/StateMachineInput/toString/#/PointingToDeclaration/"></a>[toString](to-string.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInput/toString/#/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>open override fun [toString](to-string.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br><br><br>|


## Properties  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/StateMachineInput/cppPointer/#/PointingToDeclaration/"></a>[cppPointer](cpp-pointer.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInput/cppPointer/#/PointingToDeclaration/"></a> [androidJvm] val [cppPointer](cpp-pointer.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInput/isBoolean/#/PointingToDeclaration/"></a>[isBoolean](is-boolean.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInput/isBoolean/#/PointingToDeclaration/"></a> [androidJvm] val [isBoolean](is-boolean.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)Is this input a boolean input   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInput/isNumber/#/PointingToDeclaration/"></a>[isNumber](is-number.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInput/isNumber/#/PointingToDeclaration/"></a> [androidJvm] val [isNumber](is-number.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)Is this input a number input   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInput/isTrigger/#/PointingToDeclaration/"></a>[isTrigger](is-trigger.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInput/isTrigger/#/PointingToDeclaration/"></a> [androidJvm] val [isTrigger](is-trigger.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)Is this input a boolean input   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInput/name/#/PointingToDeclaration/"></a>[name](name.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInput/name/#/PointingToDeclaration/"></a> [androidJvm] val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)Return the name given to an animation   <br>|


## Inheritors  
  
|  Name | 
|---|
| <a name="app.rive.runtime.kotlin.core/StateMachineBooleanInput///PointingToDeclaration/"></a>[StateMachineBooleanInput](../-state-machine-boolean-input/index.md)|
| <a name="app.rive.runtime.kotlin.core/StateMachineNumberInput///PointingToDeclaration/"></a>[StateMachineNumberInput](../-state-machine-number-input/index.md)|
| <a name="app.rive.runtime.kotlin.core/StateMachineTriggerInput///PointingToDeclaration/"></a>[StateMachineTriggerInput](../-state-machine-trigger-input/index.md)|

