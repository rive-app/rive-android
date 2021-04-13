//[kotlin](../../../index.md)/[app.rive.runtime.kotlin](../index.md)/[File](index.md)



# File  
 [androidJvm] class [File](index.md)(**bytes**: [ByteArray](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-byte-array/index.html))

[File](index.md)s are created in the rive editor.



This object has a counterpart in c++, which implements a lot of functionality. The  keeps track of this relationship.



You can export these .riv files and load them up. [File](index.md)s can contain multiple artboards.



If the given file cannot be loaded this will throw a [RiveException](../-rive-exception/index.md). The Rive [File](index.md) format is evolving, and while we attempt to keep backwards (and forwards) compatibility where possible, there are times when this is not possible.



The rive editor will always let you download your file in the latest runtime format.

   


## Constructors  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin/File/File/#kotlin.ByteArray/PointingToDeclaration/"></a>[File](-file.md)| <a name="app.rive.runtime.kotlin/File/File/#kotlin.ByteArray/PointingToDeclaration/"></a> [androidJvm] fun [File](-file.md)(bytes: [ByteArray](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-byte-array/index.html))   <br>|


## Functions  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin/File/artboard/#kotlin.String/PointingToDeclaration/"></a>[artboard](artboard.md)| <a name="app.rive.runtime.kotlin/File/artboard/#kotlin.String/PointingToDeclaration/"></a>[androidJvm]  <br>Content  <br>fun [artboard](artboard.md)(name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Artboard](../-artboard/index.md)  <br>More info  <br>Get the artboard called [name](artboard.md) in the file.  <br><br><br>|


## Properties  
  
|  Name |  Summary | 
|---|---|
| <a name="app.rive.runtime.kotlin/File/artboard/#/PointingToDeclaration/"></a>[artboard](artboard.md)| <a name="app.rive.runtime.kotlin/File/artboard/#/PointingToDeclaration/"></a> [androidJvm] val [artboard](artboard.md): [Artboard](../-artboard/index.md)Get the first artboard in the file.   <br>|

