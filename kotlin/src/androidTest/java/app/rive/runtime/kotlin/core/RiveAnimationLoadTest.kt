package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.RiveArtboardRenderer
import app.rive.runtime.kotlin.core.errors.AnimationException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveAnimationLoadTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var mockRenderer: RiveArtboardRenderer

    @Before
    fun init() {
        mockRenderer = TestUtils.MockArtboardRenderer()
    }

    @Test
    fun loadAnimationFirstAnimation() {
        val file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        val artboard = file.artboard("artboard1")

        val animationAlt = artboard.animation(0)
        val animation = artboard.animation("artboard1animation1")
        assertEquals(animationAlt.cppPointer, animation.cppPointer)
        assertEquals(listOf("artboard1animation1"), artboard.animationNames)
    }

    @Test
    fun loadAnimationSecondAnimation() {
        val file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        val artboard = file.artboard("artboard2")
        val artboard2animation1 = artboard.animation(0)
        val artboard2animation1Alt = artboard.animation("artboard2animation1")
        assertEquals(artboard2animation1Alt.cppPointer, artboard2animation1.cppPointer)

        val artboard2animation2 = artboard.animation(1)
        val artboard2animation2Alt = artboard.animation("artboard2animation2")
        assertEquals(artboard2animation2Alt.cppPointer, artboard2animation2.cppPointer)
        assertEquals(listOf("artboard2animation1", "artboard2animation2"), artboard.animationNames)
    }

    @Test
    fun artboardHasNoAnimations() {
        val file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        val artboard = file.firstArtboard
        assertEquals(0, artboard.animationCount)
        assertEquals(listOf<String>(), artboard.animationNames)
    }

    @Test(expected = AnimationException::class)
    fun loadFirstAnimationNoExists() {
        val file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        val artboard = file.firstArtboard
        artboard.firstAnimation
    }

    @Test(expected = AnimationException::class)
    fun loadAnimationByIndexDoesntExist() {
        val file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        val artboard = file.firstArtboard
        artboard.animation(1)
    }

    @Test(expected = AnimationException::class)
    fun loadAnimationByNameDoesntExist() {
        val file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        val artboard = file.firstArtboard
        artboard.animation("foo")
    }
}