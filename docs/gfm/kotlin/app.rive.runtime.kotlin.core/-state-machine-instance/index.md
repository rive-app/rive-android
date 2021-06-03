//[kotlin](../../../index.md)/[app.rive.runtime.kotlin.core](../index.md)/[StateMachineInstance](index.md)



# StateMachineInstance  
 [androidJvm] class [StateMachineInstance](index.md)(**stateMachine**: [StateMachine](../-state-machine/index.md)) : [PlayableInstance](../-playable-instance/index.md)

The [StateMachineInstance](index.md) is a helper to wrap common operations to play a [StateMachine](../-state-machine/index.md).



This object has a counterpart in c++, which implements a lot of functionality. The  keeps track of this relationship.



Use this to keep track of a [StateMachine](../-state-machine/index.md)s current state and progress. And to help [apply](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/index.html) changes that the [StateMachine](../-state-machine/index.md) makes to components in an [Artboard](../-artboard/index.md).

   


## Constructors  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/StateMachineInstance/#app.rive.runtime.kotlin.core.StateMachine/PointingToDeclaration/"></a>[StateMachineInstance](-state-machine-instance.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/StateMachineInstance/#app.rive.runtime.kotlin.core.StateMachine/PointingToDeclaration/"></a> [androidJvm] fun [StateMachineInstance](-state-machine-instance.md)(stateMachine: [StateMachine](../-state-machine/index.md))   <br>|


## Functions  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/_convertInput/#app.rive.runtime.kotlin.core.SMIInput/PointingToDeclaration/"></a>[_convertInput](_convert-input.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/_convertInput/#app.rive.runtime.kotlin.core.SMIInput/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [_convertInput](_convert-input.md)(input: [SMIInput](../-s-m-i-input/index.md)): [SMIInput](../-s-m-i-input/index.md)  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/_convertLayerState/#app.rive.runtime.kotlin.core.LayerState/PointingToDeclaration/"></a>[_convertLayerState](_convert-layer-state.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/_convertLayerState/#app.rive.runtime.kotlin.core.LayerState/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [_convertLayerState](_convert-layer-state.md)(state: [LayerState](../-layer-state/index.md)): [LayerState](../-layer-state/index.md)  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/advance/#app.rive.runtime.kotlin.core.Artboard#kotlin.Float/PointingToDeclaration/"></a>[advance](advance.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/advance/#app.rive.runtime.kotlin.core.Artboard#kotlin.Float/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [advance](advance.md)(artboard: [Artboard](../-artboard/index.md), elapsedTime: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)  <br>More info  <br>Advance the state machine by the [elapsedTime](advance.md) in seconds.  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/input/#kotlin.Int/PointingToDeclaration/"></a>[input](input.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/input/#kotlin.Int/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [input](input.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [SMIInput](../-s-m-i-input/index.md)  <br>More info  <br>Get the input instance at a given [index](input.md) in the [StateMachine](../-state-machine/index.md).  <br><br><br>[androidJvm]  <br>Content  <br>fun [input](input.md)(name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [SMIInput](../-s-m-i-input/index.md)  <br>More info  <br>Get the input with a given [name](input.md) in the [StateMachine](../-state-machine/index.md).  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/stateChanged/#kotlin.Int/PointingToDeclaration/"></a>[stateChanged](state-changed.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/stateChanged/#kotlin.Int/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [stateChanged](state-changed.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [LayerState](../-layer-state/index.md)  <br>More info  <br>Get a specific state changed in the last advance.  <br><br><br>|


## Properties  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/inputCount/#/PointingToDeclaration/"></a>[inputCount](input-count.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/inputCount/#/PointingToDeclaration/"></a> [androidJvm] val [inputCount](input-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Return the number of inputs configured for the state machine.   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/inputNames/#/PointingToDeclaration/"></a>[inputNames](input-names.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/inputNames/#/PointingToDeclaration/"></a> [androidJvm] val [inputNames](input-names.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)<[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)>Get the names of the stateMachineInputs in the state machine.   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/inputs/#/PointingToDeclaration/"></a>[inputs](inputs.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/inputs/#/PointingToDeclaration/"></a> [androidJvm] val [inputs](inputs.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)<[SMIInput](../-s-m-i-input/index.md)>Get the stateMachineInputs in the state machine.   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/stateChangedCount/#/PointingToDeclaration/"></a>[stateChangedCount](state-changed-count.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/stateChangedCount/#/PointingToDeclaration/"></a> [androidJvm] val [stateChangedCount](state-changed-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Return the number of states changed in the last advance.   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/stateMachine/#/PointingToDeclaration/"></a>[stateMachine](state-machine.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/stateMachine/#/PointingToDeclaration/"></a> [androidJvm] val [stateMachine](state-machine.md): [StateMachine](../-state-machine/index.md)   <br>|
| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/statesChanged/#/PointingToDeclaration/"></a>[statesChanged](states-changed.md)| <a name="app.rive.runtime.kotlin.core/StateMachineInstance/statesChanged/#/PointingToDeclaration/"></a> [androidJvm] val [statesChanged](states-changed.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)<[LayerState](../-layer-state/index.md)>Get the layer states changed in the last advance.   <br>|

