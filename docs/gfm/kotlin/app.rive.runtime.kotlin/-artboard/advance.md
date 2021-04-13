//[kotlin](../../../index.md)/[app.rive.runtime.kotlin](../index.md)/[Artboard](index.md)/[advance](advance.md)



# advance  
[androidJvm]  
Content  
fun [advance](advance.md)(elapsedTime: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/index.html))  
More info  


Advancing the artboard updates the layout for all dirty components contained in the [Artboard](index.md) updates the positions forces all components in the [Artboard](index.md) to be laid out.



Components are all the shapes, bones and groups of an [Artboard](index.md). Whenever components are added to an artboard, for example when an artboard is first loaded, they are considered dirty. Whenever animations change properties of components, move a shape or change a color, they are marked as dirty.



Before any changes to components will be visible in the next rendered frame, the artbaord needs to be [advance](advance.md)d.



[elapsedTime](advance.md) is currently not taken into account.

  



