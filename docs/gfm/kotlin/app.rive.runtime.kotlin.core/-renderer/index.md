//[kotlin](../../../index.md)/[app.rive.runtime.kotlin.core](../index.md)/[Renderer](index.md)



# Renderer  
 [androidJvm] class [Renderer](index.md)(**antialias**: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html))

A [Renderer](index.md) is used to help draw an [Artboard](../-artboard/index.md) to a [Canvas](https://developer.android.com/reference/kotlin/android/graphics/Canvas.html)



This object has a counterpart in c++, which implements a lot of functionality. The [cppPointer](cpp-pointer.md) keeps track of this relationship.



Most of the functions implemented here are called from the c++ layer when artboards are rendered.

   


## Constructors  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin.core/Renderer/Renderer/#kotlin.Boolean/PointingToDeclaration/"></a>[Renderer](-renderer.md)| <a name="app.rive.runtime.kotlin.core/Renderer/Renderer/#kotlin.Boolean/PointingToDeclaration/"></a> [androidJvm] fun [Renderer](-renderer.md)(antialias: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = true)   <br>|


## Functions  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/Renderer/align/#app.rive.runtime.kotlin.core.Fit#app.rive.runtime.kotlin.core.Alignment#app.rive.runtime.kotlin.core.AABB#app.rive.runtime.kotlin.core.AABB/PointingToDeclaration/"></a>[align](align.md)| <a name="app.rive.runtime.kotlin.core/Renderer/align/#app.rive.runtime.kotlin.core.Fit#app.rive.runtime.kotlin.core.Alignment#app.rive.runtime.kotlin.core.AABB#app.rive.runtime.kotlin.core.AABB/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [align](align.md)(fit: [Fit](../-fit/index.md), alignment: [Alignment](../-alignment/index.md), targetBounds: [AABB](../-a-a-b-b/index.md), sourceBounds: [AABB](../-a-a-b-b/index.md))  <br>More info  <br>Instruct the cpp renderer how to align the artboard in the available space [targetBounds](align.md).  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Renderer/cleanup/#/PointingToDeclaration/"></a>[cleanup](cleanup.md)| <a name="app.rive.runtime.kotlin.core/Renderer/cleanup/#/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [cleanup](cleanup.md)()  <br>More info  <br>Remove the [Renderer](index.md) object from memory.  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Renderer/clipPath/#android.graphics.Path/PointingToDeclaration/"></a>[clipPath](clip-path.md)| <a name="app.rive.runtime.kotlin.core/Renderer/clipPath/#android.graphics.Path/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [clipPath](clip-path.md)(path: [Path](https://developer.android.com/reference/kotlin/android/graphics/Path.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)  <br>More info  <br>Passthrough to apply [clipPath](clip-path.md) to the [canvas](canvas.md)This function is used by the c++ layer.  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Renderer/drawPath/#android.graphics.Path#android.graphics.Paint/PointingToDeclaration/"></a>[drawPath](draw-path.md)| <a name="app.rive.runtime.kotlin.core/Renderer/drawPath/#android.graphics.Path#android.graphics.Paint/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [drawPath](draw-path.md)(path: [Path](https://developer.android.com/reference/kotlin/android/graphics/Path.html), paint: [Paint](https://developer.android.com/reference/kotlin/android/graphics/Paint.html))  <br>More info  <br>Passthrough to apply [drawPath](draw-path.md) to the [canvas](canvas.md)This function is used by the c++ layer.  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Renderer/restore/#/PointingToDeclaration/"></a>[restore](restore.md)| <a name="app.rive.runtime.kotlin.core/Renderer/restore/#/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [restore](restore.md)()  <br>More info  <br>Passthrough to apply [restore](restore.md) to the [canvas](canvas.md)This function is used by the c++ layer.  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Renderer/save/#/PointingToDeclaration/"></a>[save](save.md)| <a name="app.rive.runtime.kotlin.core/Renderer/save/#/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [save](save.md)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)  <br>More info  <br>Passthrough to apply [save](save.md) to the [canvas](canvas.md)This function is used by the c++ layer.  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Renderer/setMatrix/#android.graphics.Matrix/PointingToDeclaration/"></a>[setMatrix](set-matrix.md)| <a name="app.rive.runtime.kotlin.core/Renderer/setMatrix/#android.graphics.Matrix/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [setMatrix](set-matrix.md)(matrix: [Matrix](https://developer.android.com/reference/kotlin/android/graphics/Matrix.html))  <br>More info  <br>Passthrough to apply [matrix](set-matrix.md) to the [canvas](canvas.md)This function is used by the c++ layer.  <br><br><br>|
| <a name="app.rive.runtime.kotlin.core/Renderer/translate/#kotlin.Float#kotlin.Float/PointingToDeclaration/"></a>[translate](translate.md)| <a name="app.rive.runtime.kotlin.core/Renderer/translate/#kotlin.Float#kotlin.Float/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [translate](translate.md)(dx: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html), dy: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html))  <br>More info  <br>Passthrough to apply [translate](translate.md) to the [canvas](canvas.md)This function is used by the c++ layer.  <br><br><br>|


## Properties  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/Renderer/canvas/#/PointingToDeclaration/"></a>[canvas](canvas.md)| <a name="app.rive.runtime.kotlin.core/Renderer/canvas/#/PointingToDeclaration/"></a> [androidJvm] lateinit var [canvas](canvas.md): [Canvas](https://developer.android.com/reference/kotlin/android/graphics/Canvas.html)   <br>|
| <a name="app.rive.runtime.kotlin.core/Renderer/cppPointer/#/PointingToDeclaration/"></a>[cppPointer](cpp-pointer.md)| <a name="app.rive.runtime.kotlin.core/Renderer/cppPointer/#/PointingToDeclaration/"></a> [androidJvm] var [cppPointer](cpp-pointer.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)   <br>|

