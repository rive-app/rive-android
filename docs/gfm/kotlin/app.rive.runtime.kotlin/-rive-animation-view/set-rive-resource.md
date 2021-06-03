//[kotlin](../../../index.md)/[app.rive.runtime.kotlin](../index.md)/[RiveAnimationView](index.md)/[setRiveResource](set-rive-resource.md)



# setRiveResource  
[androidJvm]  
Content  
fun [setRiveResource](set-rive-resource.md)(@[RawRes](https://developer.android.com/reference/kotlin/androidx/annotation/RawRes.html)()resId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), artboardName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, animationName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, stateMachineName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, autoplay: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = drawable.autoplay, fit: [Fit](../../app.rive.runtime.kotlin.core/-fit/index.md) = Fit.CONTAIN, alignment: [Alignment](../../app.rive.runtime.kotlin.core/-alignment/index.md) = Alignment.CENTER, loop: [Loop](../../app.rive.runtime.kotlin.core/-loop/index.md) = Loop.AUTO)  
More info  


Load the [resource Id](set-rive-resource.md) as a rive file and load it into the view.

<ul><li>Optionally provide an [artboardName](set-rive-resource.md) to use, or the first artboard in the file.</li><li>Optionally provide an [animationName](set-rive-resource.md) to load by default, playing without any suggested animations names will simply play the first animaiton</li><li>Enable [autoplay](set-rive-resource.md) to start the animation without further prompts.</li><li>Configure [alignment](set-rive-resource.md) to specify how the animation should be aligned to its container.</li><li>Configure [fit](set-rive-resource.md) to specify how and if the animation should be resized to fit its container.</li><li>Configure [loop](set-rive-resource.md) to configure if animations should loop, play once, or pingpong back and forth. Defaults to the setup in the rive file.</li></ul>

#### Throws  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin/RiveAnimationView/setRiveResource/#kotlin.Int#kotlin.String?#kotlin.String?#kotlin.String?#kotlin.Boolean#app.rive.runtime.kotlin.core.Fit#app.rive.runtime.kotlin.core.Alignment#app.rive.runtime.kotlin.core.Loop/PointingToDeclaration/"></a>[app.rive.runtime.kotlin.core.RiveException](../../app.rive.runtime.kotlin.core/-rive-exception/index.md)| <a name="app.rive.runtime.kotlin/RiveAnimationView/setRiveResource/#kotlin.Int#kotlin.String?#kotlin.String?#kotlin.String?#kotlin.Boolean#app.rive.runtime.kotlin.core.Fit#app.rive.runtime.kotlin.core.Alignment#app.rive.runtime.kotlin.core.Loop/PointingToDeclaration/"></a><br><br>if [artboardName](set-rive-resource.md) or [animationName](set-rive-resource.md) are set and do not exist in the file.<br><br>|
  



