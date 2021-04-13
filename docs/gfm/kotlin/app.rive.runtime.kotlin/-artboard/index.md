//[kotlin](../../../index.md)/[app.rive.runtime.kotlin](../index.md)/[Artboard](index.md)



# Artboard  
 [androidJvm] class [Artboard](index.md)(**nativePointer**: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))

[Artboard](index.md)s as designed in the Rive animation editor.



This object has a counterpart in c++, which implements a lot of functionality. The [nativePointer](native-pointer.md) keeps track of this relationship.



[Artboard](index.md)s provide access to available [Animation](../-animation/index.md)s, and some basic properties. You can [draw](draw.md) artboards using a [Renderer](../-renderer/index.md) that is tied to a canvas.



The constructor uses a [nativePointer](native-pointer.md) to point to its c++ counterpart object.

   


## Constructors  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin/Artboard/Artboard/#kotlin.Long/PointingToDeclaration/"></a>[Artboard](-artboard.md)| <a name="app.rive.runtime.kotlin/Artboard/Artboard/#kotlin.Long/PointingToDeclaration/"></a> [androidJvm] fun [Artboard](-artboard.md)(nativePointer: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))   <br>|


## Functions  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin/Artboard/advance/#kotlin.Float/PointingToDeclaration/"></a>[advance](advance.md)| <a name="app.rive.runtime.kotlin/Artboard/advance/#kotlin.Float/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [advance](advance.md)(elapsedTime: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html))  <br>More info  <br>Advancing the artboard updates the layout for all dirty components contained in the [Artboard](index.md) updates the positions forces all components in the [Artboard](index.md) to be laid out.  <br><br><br>|
| <a name="app.rive.runtime.kotlin/Artboard/animation/#kotlin.Int/PointingToDeclaration/"></a>[animation](animation.md)| <a name="app.rive.runtime.kotlin/Artboard/animation/#kotlin.Int/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [animation](animation.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [Animation](../-animation/index.md)  <br>More info  <br>Get the animation at a given [index](animation.md) in the [Artboard](index.md).  <br><br><br>[androidJvm]  <br>Content  <br>fun [animation](animation.md)(name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Animation](../-animation/index.md)  <br>More info  <br>Get the animation with a given [name](animation.md) in the [Artboard](index.md).  <br><br><br>|
| <a name="app.rive.runtime.kotlin/Artboard/draw/#app.rive.runtime.kotlin.Renderer/PointingToDeclaration/"></a>[draw](draw.md)| <a name="app.rive.runtime.kotlin/Artboard/draw/#app.rive.runtime.kotlin.Renderer/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [draw](draw.md)(renderer: [Renderer](../-renderer/index.md))  <br>More info  <br>Draw the the artboard to the [renderer](draw.md).  <br><br><br>|


## Properties  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin/Artboard/animationCount/#/PointingToDeclaration/"></a>[animationCount](animation-count.md)| <a name="app.rive.runtime.kotlin/Artboard/animationCount/#/PointingToDeclaration/"></a> [androidJvm] val [animationCount](animation-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)Get the number of animations stored inside the [Artboard](index.md).   <br>|
| <a name="app.rive.runtime.kotlin/Artboard/bounds/#/PointingToDeclaration/"></a>[bounds](bounds.md)| <a name="app.rive.runtime.kotlin/Artboard/bounds/#/PointingToDeclaration/"></a> [androidJvm] val [bounds](bounds.md): [AABB](../-a-a-b-b/index.md)Get the bounds of Artboard as defined in the rive editor.   <br>|
| <a name="app.rive.runtime.kotlin/Artboard/firstAnimation/#/PointingToDeclaration/"></a>[firstAnimation](first-animation.md)| <a name="app.rive.runtime.kotlin/Artboard/firstAnimation/#/PointingToDeclaration/"></a> [androidJvm] val [firstAnimation](first-animation.md): [Animation](../-animation/index.md)Get the first [Animation](../-animation/index.md) of the [Artboard](index.md).   <br>|
| <a name="app.rive.runtime.kotlin/Artboard/name/#/PointingToDeclaration/"></a>[name](name.md)| <a name="app.rive.runtime.kotlin/Artboard/name/#/PointingToDeclaration/"></a> [androidJvm] val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)Get the [name](name.md) of the Artboard.   <br>|
| <a name="app.rive.runtime.kotlin/Artboard/nativePointer/#/PointingToDeclaration/"></a>[nativePointer](native-pointer.md)| <a name="app.rive.runtime.kotlin/Artboard/nativePointer/#/PointingToDeclaration/"></a> [androidJvm] val [nativePointer](native-pointer.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)   <br>|

