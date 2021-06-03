//[kotlin](../../../index.md)/[app.rive.runtime.kotlin](../index.md)/[RiveAnimationView](index.md)/[stop](stop.md)



# stop  
[androidJvm]  
Content  
fun [stop](stop.md)()  
More info  


Stops all [animation instances](../../app.rive.runtime.kotlin.core/-linear-animation-instance/index.md).



Animations Instances will be disposed of completely. Subsequent plays will create new [animation instances](../../app.rive.runtime.kotlin.core/-linear-animation-instance/index.md) for the [animations](../../app.rive.runtime.kotlin.core/-animation/index.md) in the file.

  


[androidJvm]  
Content  
fun [stop](stop.md)(animationNames: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)<[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)>, areStateMachines: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false)  
More info  


Stops any [animation instances](../../app.rive.runtime.kotlin.core/-linear-animation-instance/index.md) for [animations](../../app.rive.runtime.kotlin.core/-animation/index.md) with any of the provided [names](stop.md).



Animations Instances will be disposed of completely. Subsequent plays will create new [animation instances](../../app.rive.runtime.kotlin.core/-linear-animation-instance/index.md) for the [animations](../../app.rive.runtime.kotlin.core/-animation/index.md) in the file.



Advanced: Multiple [animation instances](../../app.rive.runtime.kotlin.core/-linear-animation-instance/index.md) can running the same [animation](../../app.rive.runtime.kotlin.core/-animation/index.md)

  


[androidJvm]  
Content  
fun [stop](stop.md)(animationName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), isStateMachine: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false)  
More info  


Stops any [animation instances](../../app.rive.runtime.kotlin.core/-linear-animation-instance/index.md) for an [animation](../../app.rive.runtime.kotlin.core/-animation/index.md) called [animationName](stop.md).



Animations Instances will be disposed of completely. Subsequent plays will create new [animation instances](../../app.rive.runtime.kotlin.core/-linear-animation-instance/index.md) for the [animations](../../app.rive.runtime.kotlin.core/-animation/index.md) in the file.



Advanced: Multiple [animation instances](../../app.rive.runtime.kotlin.core/-linear-animation-instance/index.md) can running the same [animation](../../app.rive.runtime.kotlin.core/-animation/index.md)

  



