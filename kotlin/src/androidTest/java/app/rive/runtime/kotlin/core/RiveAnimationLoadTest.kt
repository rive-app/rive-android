package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.core.errors.*
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveAnimationLoadTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

    @Test
    fun loadAnimationFirstAnimation() {
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        var artboard = file.artboard("artboard1")

        var animationAlt = artboard.animation(0)
        var animation = artboard.animation("artboard1animation1")
        assertEquals(animationAlt.cppPointer, animation.cppPointer)
        assertEquals(listOf("artboard1animation1"), artboard.animationNames)
    }

    @Test
    fun loadAnimationSecondAnimation() {
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        var artboard = file.artboard("artboard2")
        var artboard2animation1 = artboard.animation(0)
        var artboard2animation1Alt = artboard.animation("artboard2animation1")
        assertEquals(artboard2animation1Alt.cppPointer, artboard2animation1.cppPointer)

        var artboard2animation2 = artboard.animation(1)
        var artboard2animation2Alt = artboard.animation("artboard2animation2")
        assertEquals(artboard2animation2Alt.cppPointer, artboard2animation2.cppPointer)
        assertEquals(listOf("artboard2animation1", "artboard2animation2"), artboard.animationNames)
    }

    @Test
    fun artboardHasNoAnimations() {
        var file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        var artboard = file.firstArtboard
        assertEquals(0, artboard.animationCount)
        assertEquals(listOf<String>(), artboard.animationNames)
    }

    @Test(expected = AnimationException::class)
    fun loadFirstAnimationNoExists() {
        var file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        var artboard = file.firstArtboard
        artboard.firstAnimation
    }

    @Test(expected = AnimationException::class)
    fun loadAnimationByIndexDoesntExist() {
        var file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        var artboard = file.firstArtboard
        artboard.animation(1)
    }

    @Test(expected = AnimationException::class)
    fun loadAnimationByNameDoesntExist() {
        var file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        var artboard = file.firstArtboard
        artboard.animation("foo")
    }
}