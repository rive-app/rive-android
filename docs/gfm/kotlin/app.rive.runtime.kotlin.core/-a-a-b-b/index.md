//[kotlin](../../../index.md)/[app.rive.runtime.kotlin.core](../index.md)/[AABB](index.md)



# AABB  
 [androidJvm] class [AABB](index.md)

Representation of an axis-aligned bounding box (AABB).



This object has a counterpart in c++, which implements a lot of functionality. The [cppPointer](cpp-pointer.md) keeps track of this relationship.



The AABB helps us describe and keep track of shapes and artboards, by describing the top left and bottom right vertices of a box.

   


## Constructors  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin.core/AABB/AABB/#kotlin.Long/PointingToDeclaration/"></a>[AABB](-a-a-b-b.md)| <a name="app.rive.runtime.kotlin.core/AABB/AABB/#kotlin.Long/PointingToDeclaration/"></a> [androidJvm] fun [AABB](-a-a-b-b.md)(_cppPointer: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))   <br>|
| <a name="app.rive.runtime.kotlin.core/AABB/AABB/#kotlin.Float#kotlin.Float/PointingToDeclaration/"></a>[AABB](-a-a-b-b.md)| <a name="app.rive.runtime.kotlin.core/AABB/AABB/#kotlin.Float#kotlin.Float/PointingToDeclaration/"></a> [androidJvm] fun [AABB](-a-a-b-b.md)(width: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html), height: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html))   <br>|


## Properties  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin.core/AABB/cppPointer/#/PointingToDeclaration/"></a>[cppPointer](cpp-pointer.md)| <a name="app.rive.runtime.kotlin.core/AABB/cppPointer/#/PointingToDeclaration/"></a> [androidJvm] var [cppPointer](cpp-pointer.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)   <br>|
| <a name="app.rive.runtime.kotlin.core/AABB/height/#/PointingToDeclaration/"></a>[height](height.md)| <a name="app.rive.runtime.kotlin.core/AABB/height/#/PointingToDeclaration/"></a> [androidJvm] val [height](height.md): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html)Return the height of the bounding box   <br>|
| <a name="app.rive.runtime.kotlin.core/AABB/width/#/PointingToDeclaration/"></a>[width](width.md)| <a name="app.rive.runtime.kotlin.core/AABB/width/#/PointingToDeclaration/"></a> [androidJvm] val [width](width.md): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html)Return the width of the bounding box   <br>|

