package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.errors.ArtboardException
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveViewTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var mockView: RiveAnimationView

    @Before
    fun initView() {
        mockView = TestUtils.MockRiveAnimationView(appContext)
    }

    @Test
    fun viewNoDefaults() {
        UiThreadStatement.runOnUiThread {
            assertFalse(mockView.isPlaying)
            assertNull(mockView.controller.file)
        }
    }

    @Test
    fun viewDefaultsLoadResource() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards, autoplay = false)
            assertNotNull(mockView.controller.file)
            mockView.play(listOf("artboard2animation1", "artboard2animation2"))

            assertTrue(mockView.isPlaying)
            assertTrue(mockView.controller.isActive)
            assertEquals(listOf("artboard2", "artboard1"), mockView.file?.artboardNames)
            assertEquals(
                listOf("artboard2animation1", "artboard2animation2"),
                mockView.animations.map { it.name }.toList()
            )
        }
    }

    @Test
    fun viewDefaultsChangeArtboard() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards)
            assertEquals(true, mockView.isPlaying)
            mockView.artboardName = "artboard1"
            assertEquals(
                listOf("artboard1animation1"),
                mockView.animations.map { it.name }.toList()
            )
        }
    }

    @Test(expected = ArtboardException::class)
    fun viewChangeToMissingArtboard() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards)
            mockView.artboardName = "artboardDoesntExist"
        }

    }

    @Test
    fun viewDefaultsNoAutoplay() {
        UiThreadStatement.runOnUiThread {
            mockView.autoplay = false
            mockView.setRiveResource(R.raw.multipleartboards)
            assert(!mockView.isPlaying)
            mockView.artboardName = "artboard2"
            assertEquals(
                listOf<String>(),
                mockView.animations.map { it.name }.toList()
            )
            mockView.play(listOf("artboard2animation1", "artboard2animation2"))
            assertEquals(
                listOf("artboard2animation1", "artboard2animation2"),
                mockView.animations.map { it.name }.toList()
            )
        }
    }

    @Test
    fun viewPause() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards)
            assertEquals(true, mockView.isPlaying)
            assertEquals(1, mockView.animations.size)
            assertEquals(1, mockView.playingAnimations.size)
            mockView.pause()
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Pause happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assert(!mockView.isPlaying)
            assertEquals(1, mockView.animations.size)
            assertEquals(0, mockView.playingAnimations.size)
        }
    }


    @Test
    fun viewPauseOneByOne() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            mockView.play(listOf("one", "two", "three", "four"))

            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            mockView.pause("junk")
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                hashSetOf("one", "two", "three", "four")
            )

            mockView.pause("one")
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                hashSetOf("two", "three", "four")
            )
            mockView.pause("two")
            assertEquals(true, mockView.isPlaying)

            mockView.pause("three")
            assertEquals(true, mockView.isPlaying)

            mockView.pause("four")
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Pause happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assert(!mockView.isPlaying)

            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                emptySet<String>()
            )
        }
    }

    @Test
    fun viewPauseMultiple() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            mockView.play(listOf("one", "two", "three", "four"))
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                hashSetOf("one", "two", "three", "four")
            )

            mockView.pause(listOf("one", "three"))
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                hashSetOf("two", "four")
            )

            mockView.pause(listOf("two", "four"))
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Pause happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assert(!mockView.isPlaying)
            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                emptySet<String>()
            )
        }
    }

    @Test
    fun viewPlay() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards, autoplay = false)
            assert(!mockView.isPlaying)
            assertEquals(0, mockView.animations.size)
            assertEquals(0, mockView.playingAnimations.size)
            mockView.play()
            assertEquals(true, mockView.isPlaying)
            assertEquals(1, mockView.animations.size)
            assertEquals(1, mockView.playingAnimations.size)
        }
    }

    @Test
    fun viewPlayOneByOne() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assert(!mockView.isPlaying)
            assertEquals(
                emptySet<String>(),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            mockView.play("one")
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("one"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
        }
    }

    @Test(expected = RiveException::class)
    fun viewPlayJunk() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assert(!mockView.isPlaying)
            assertEquals(
                emptySet<String>(),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            mockView.play("junk")
        }
    }

    @Test
    fun viewPlayMultiple() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assert(!mockView.isPlaying)
            assertEquals(
                emptySet<String>(),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            mockView.play(listOf("one", "two"))
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("one", "two"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
        }
    }

    @Test
    fun viewPlayLoopMode() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)

            // Mode dictated by animation.
            mockView.play("one")
            assertEquals(Loop.ONESHOT, mockView.playingAnimations.first().loop)

            // forced loop
            mockView.play("one", Loop.LOOP)
            assertEquals(Loop.LOOP, mockView.playingAnimations.first().loop)

            // mode unchanged.
            mockView.play("one")
            assertEquals(Loop.LOOP, mockView.playingAnimations.first().loop)

            // mode unchanged.
            mockView.play("one", Loop.PINGPONG)
            assertEquals(Loop.PINGPONG, mockView.playingAnimations.first().loop)

            // mode unchanged.
            mockView.play("one", Loop.ONESHOT)
            assertEquals(Loop.ONESHOT, mockView.playingAnimations.first().loop)
        }
    }

    @Test
    fun viewPlayDirection() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)

            // setting auto direction doesn't change direction
            mockView.play("one")
            assertEquals(Direction.FORWARDS, mockView.playingAnimations.first().direction)
            mockView.play("one", direction = Direction.AUTO)
            assertEquals(Direction.FORWARDS, mockView.playingAnimations.first().direction)
            mockView.play("one", direction = Direction.BACKWARDS)
            assertEquals(Direction.BACKWARDS, mockView.playingAnimations.first().direction)
            mockView.play("one", direction = Direction.AUTO)
            assertEquals(Direction.BACKWARDS, mockView.playingAnimations.first().direction)
            mockView.pause("one")

            // PingPong cycles between forwards and backwards
            mockView.play("two", loop = Loop.PINGPONG)
            assertEquals(Direction.FORWARDS, mockView.playingAnimations.first().direction)
            assert(mockView.artboardRenderer != null)
            mockView.artboardRenderer!!.advance(1001f)
            assertEquals(Direction.BACKWARDS, mockView.playingAnimations.first().direction)

        }
    }


    @Test
    fun viewSetResourceLoadArtboard() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations)
            assert(mockView.artboardRenderer != null)
            assertEquals(
                listOf("four", "three", "two", "one"),
                mockView.artboardRenderer?.file?.firstArtboard?.animationNames
            )

            mockView.setRiveResource(R.raw.multipleartboards)
            assertEquals(
                listOf("artboard2animation1", "artboard2animation2"),
                mockView.artboardRenderer?.file?.firstArtboard?.animationNames
            )
        }
    }

    @Test
    fun viewSetResourceLoadArtboardArtboardGotcha() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, artboardName = "New Artboard")
            mockView.setRiveResource(R.raw.multipleartboards)
        }
    }

    @Test
    fun viewSetResourceLoadArtboardArtboardGotchaOK() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, artboardName = "New Artboard")
            mockView.setRiveResource(R.raw.multipleartboards, artboardName = "artboard1")
        }
    }


    @Test
    fun viewStop() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards)
            assertEquals(true, mockView.isPlaying)
            assertEquals(1, mockView.animations.size)
            assertEquals(1, mockView.playingAnimations.size)

            mockView.stop()
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Stop happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assert(!mockView.isPlaying)
            assertEquals(0, mockView.animations.size)
            assertEquals(0, mockView.playingAnimations.size)
        }
    }

    @Test
    fun viewStopMultiple() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            mockView.play(listOf("one", "two", "three", "four"))
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                mockView.playingAnimations.map { it.name }.toHashSet(),
            )
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                mockView.animations.map { it.name }.toHashSet(),
            )

            mockView.stop(listOf("one", "three"))
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("two", "four"),
                mockView.playingAnimations.map { it.name }.toHashSet(),
            )
            assertEquals(
                hashSetOf("two", "four"),
                mockView.animations.map { it.name }.toHashSet(),
            )

            mockView.stop(listOf("two", "four"))
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Stop happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assert(!mockView.isPlaying)
            assertEquals(
                hashSetOf<String>(),
                mockView.playingAnimations.map { it.name }.toHashSet(),
            )
            assertEquals(
                hashSetOf<String>(),
                mockView.animations.map { it.name }.toHashSet(),
            )
        }
    }

    @Test
    fun viewStopOneByOne() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            mockView.play(listOf("one", "two", "three", "four"))

            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                mockView.animations.map { it.name }.toHashSet()
            )
            mockView.stop("junk")
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                mockView.animations.map { it.name }.toHashSet()
            )

            mockView.stop("one")
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("two", "three", "four"),
                mockView.animations.map { it.name }.toHashSet()
            )

            mockView.stop("two")
            assertEquals(true, mockView.isPlaying)

            mockView.stop("three")
            assertEquals(true, mockView.isPlaying)

            mockView.stop("four")
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Stop happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assert(!mockView.isPlaying)

            assertEquals(
                emptySet<String>(),
                mockView.animations.map { it.name }.toHashSet()
            )
        }
    }

    @Test
    fun viewStopAnimationDetailsTime() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)

            assert(mockView.artboardRenderer != null)
            val renderer = mockView.artboardRenderer!!
            mockView.play("one", Loop.PINGPONG)
            renderer.advance(0.1f)

            assertEquals(0.1f, mockView.animations.first().time)

            assert(renderer.isPlaying)
            mockView.stop("one")
            renderer.scheduleFrame()
            assert(!renderer.isPlaying)
            mockView.play("one")
            assertEquals(0.0f, mockView.animations.first().time)
            assertEquals(Loop.ONESHOT, mockView.animations.first().loop)
        }
    }

    @Test
    fun viewPauseAnimationDetailsTime() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assert(!mockView.isPlaying)

            mockView.play("one", Loop.PINGPONG)

            assert(mockView.artboardRenderer != null)
            mockView.artboardRenderer!!.advance(0.1f)
            assertEquals(0.1f, mockView.animations.first().time)

            mockView.pause("one")
            mockView.play("one")
            assertEquals(0.1f, mockView.animations.first().time)
            assertEquals(Loop.PINGPONG, mockView.animations.first().loop)
        }
    }

    @Test
    fun viewReset() {
        // pretty basic test. we could start seeing if the artboards properties are reset properly
        // but we actually would need to expose a lot more of that to do this.
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)

            assert(mockView.artboardRenderer != null)
            mockView.play("one", Loop.PINGPONG)
            val originalPointer = mockView.artboardRenderer!!.activeArtboard?.cppPointer
            mockView.reset()
            assertNotEquals(mockView.artboardRenderer!!.activeArtboard?.cppPointer, originalPointer)
            assert(!mockView.isPlaying)
        }
    }

    @Test
    fun viewResetTwice() {
        // This used to crash with a null pointer dereference.
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            mockView.reset()
            mockView.reset()
        }
    }

    @Test
    fun viewResetAutoplay() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = true)
            assertEquals(true, mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            val originalPointer = mockView.artboardRenderer!!.activeArtboard?.cppPointer
            mockView.reset()
            assertNotEquals(mockView.artboardRenderer!!.activeArtboard?.cppPointer, originalPointer)
            assertEquals(true, mockView.isPlaying)
        }
    }
}