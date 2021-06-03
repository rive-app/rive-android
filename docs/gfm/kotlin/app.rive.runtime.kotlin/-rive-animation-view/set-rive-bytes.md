//[kotlin](../../../index.md)/[app.rive.runtime.kotlin](../index.md)/[RiveAnimationView](index.md)/[setRiveBytes](set-rive-bytes.md)



# setRiveBytes  
[androidJvm]  
Content  
fun [setRiveBytes](set-rive-bytes.md)(bytes: [ByteArray](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-byte-array/index.html), artboardName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, animationName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, stateMachineName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, autoplay: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = drawable.autoplay, fit: [Fit](../../app.rive.runtime.kotlin.core/-fit/index.md) = Fit.CONTAIN, alignment: [Alignment](../../app.rive.runtime.kotlin.core/-alignment/index.md) = Alignment.CENTER, loop: [Loop](../../app.rive.runtime.kotlin.core/-loop/index.md) = Loop.AUTO)  
More info  


Create a view file from a byte array and load it into the view

<ul><li>Optionally provide an [artboardName](set-rive-bytes.md) to use, or the first artboard in the file.</li><li>Optionally provide an [animationName](set-rive-bytes.md) to load by default, playing without any suggested animations names will simply play the first animaiton</li><li>Enable [autoplay](set-rive-bytes.md) to start the animation without further prompts.</li><li>Configure [alignment](set-rive-bytes.md) to specify how the animation should be aligned to its container.</li><li>Configure [fit](set-rive-bytes.md) to specify how and if the animation should be resized to fit its container.</li><li>Configure [loop](set-rive-bytes.md) to configure if animations should loop, play once, or pingpong back and forth. Defaults to the setup in the rive file.</li></ul>

#### Throws  
  
| | |
|---|---|
| <a name="app.rive.runtime.kotlin/RiveAnimationView/setRiveBytes/#kotlin.ByteArray#kotlin.String?#kotlin.String?#kotlin.String?#kotlin.Boolean#app.rive.runtime.kotlin.core.Fit#app.rive.runtime.kotlin.core.Alignment#app.rive.runtime.kotlin.core.Loop/PointingToDeclaration/"></a>[app.rive.runtime.kotlin.core.RiveException](../../app.rive.runtime.kotlin.core/-rive-exception/index.md)| <a name="app.rive.runtime.kotlin/RiveAnimationView/setRiveBytes/#kotlin.ByteArray#kotlin.String?#kotlin.String?#kotlin.String?#kotlin.Boolean#app.rive.runtime.kotlin.core.Fit#app.rive.runtime.kotlin.core.Alignment#app.rive.runtime.kotlin.core.Loop/PointingToDeclaration/"></a><br><br>if [artboardName](set-rive-bytes.md) or [animationName](set-rive-bytes.md) are set and do not exist in the file.<br><br>|
  



