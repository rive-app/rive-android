package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveViewTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

    @Test
    fun viewNoDefaults() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)

            assertEquals(false, view.isPlaying)
        }
    }

    @Test
    fun viewDefaultsLoadResource() {
        UiThreadStatement.runOnUiThread {

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards, autoplay = false)
            view.play(listOf("artboard2animation1", "artboard2animation2"))

            assertEquals(true, view.isPlaying)
            assertEquals(listOf("artboard2", "artboard1"), view.file?.artboardNames)
            assertEquals(
                listOf("artboard2animation1", "artboard2animation2"),
                view.animations.map { it.animation.name }.toList()
            )
        }
    }

    @Test
    fun viewDefaultsChangeArtboard() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(true, view.isPlaying)
            view.artboardName = "artboard1"
            assertEquals(
                listOf("artboard1animation1"),
                view.animations.map { it.animation.name }.toList()
            )
        }

    }

    @Test
    fun viewDefaultsNoAutoplay() {
        UiThreadStatement.runOnUiThread {

            val view = RiveAnimationView(appContext)
            view.autoplay = false
            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(false, view.isPlaying)
            view.artboardName = "artboard2"
            assertEquals(
                listOf<String>(),
                view.animations.map { it.animation.name }.toList()
            )
            view.play(listOf("artboard2animation1", "artboard2animation2"))
            assertEquals(
                listOf("artboard2animation1", "artboard2animation2"),
                view.animations.map { it.animation.name }.toList()
            )
        }
    }

    @Test
    fun viewPause() {
        UiThreadStatement.runOnUiThread {

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(true, view.isPlaying)
            assertEquals(1, view.animations.size)
            assertEquals(1, view.playingAnimations.size)
            view.pause()
            assertEquals(false, view.isPlaying)
            assertEquals(1, view.animations.size)
            assertEquals(0, view.playingAnimations.size)
        }
    }


    @Test
    fun viewPauseOneByOne() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)
            view.play(listOf("one", "two", "three", "four"))

            assertEquals(true, view.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                view.playingAnimations.map { it.animation.name }.toHashSet()
            )
            view.pause("junk")
            assertEquals(true, view.isPlaying)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("one", "two", "three", "four")
            )

            view.pause("one")
            assertEquals(true, view.isPlaying)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("two", "three", "four")
            )
            view.pause("two")
            assertEquals(true, view.isPlaying)

            view.pause("three")
            assertEquals(true, view.isPlaying)

            view.pause("four")
            assertEquals(false, view.isPlaying)

            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf<LinearAnimationInstance>()
            )
        }
    }

    @Test
    fun viewPauseMultiple() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)
            view.play(listOf("one", "two", "three", "four"))
            assertEquals(true, view.isPlaying)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("one", "two", "three", "four")
            )

            view.pause(listOf("one", "three"))
            assertEquals(true, view.isPlaying)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("two", "four")
            )

            view.pause(listOf("two", "four"))
            assertEquals(false, view.isPlaying)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf<LinearAnimationInstance>()
            )
        }
    }

    @Test
    fun viewPlay() {
        UiThreadStatement.runOnUiThread {

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards, autoplay = false)
            assertEquals(false, view.isPlaying)
            assertEquals(0, view.animations.size)
            assertEquals(0, view.playingAnimations.size)
            view.play()
            assertEquals(true, view.isPlaying)
            assertEquals(1, view.animations.size)
            assertEquals(1, view.playingAnimations.size)
        }
    }

    @Test
    fun viewPlayOneByOne() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assertEquals(false, view.isPlaying)
            assertEquals(
                hashSetOf<LinearAnimationInstance>(),
                view.playingAnimations.map { it.animation.name }.toHashSet()
            )
            view.play("one")
            assertEquals(true, view.isPlaying)
            assertEquals(
                hashSetOf("one"),
                view.playingAnimations.map { it.animation.name }.toHashSet()
            )
        }
    }

    @Test(expected = RiveException::class)
    fun viewPlayJunk() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assertEquals(false, view.isPlaying)
            assertEquals(
                hashSetOf<LinearAnimationInstance>(),
                view.playingAnimations.map { it.animation.name }.toHashSet()
            )
            view.play("junk")
        }
    }

    @Test
    fun viewPlayMultiple() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assertEquals(false, view.isPlaying)
            assertEquals(
                hashSetOf<LinearAnimationInstance>(),
                view.playingAnimations.map { it.animation.name }.toHashSet()
            )
            view.play(listOf("one", "two"))
            assertEquals(true, view.isPlaying)
            assertEquals(
                hashSetOf("one", "two"),
                view.playingAnimations.map { it.animation.name }.toHashSet()
            )
        }
    }

    @Test
    fun viewPlayLoopMode() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)

            // Mode dictated by animation.
            view.play("one")
            assertEquals(Loop.ONESHOT, view.playingAnimations.first().loop)

            // forced loop
            view.play("one", Loop.LOOP)
            assertEquals(Loop.LOOP, view.playingAnimations.first().loop)

            // mode unchanged.
            view.play("one")
            assertEquals(Loop.LOOP, view.playingAnimations.first().loop)

            // mode unchanged.
            view.play("one", Loop.PINGPONG)
            assertEquals(Loop.PINGPONG, view.playingAnimations.first().loop)

            // mode unchanged.
            view.play("one", Loop.ONESHOT)
            assertEquals(Loop.ONESHOT, view.playingAnimations.first().loop)
        }
    }

    @Test
    fun viewPlayDirection() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)

            // setting auto direction doesn't change direction
            view.play("one")
            assertEquals(Direction.FORWARDS, view.playingAnimations.first().direction)
            view.play("one", direction = Direction.AUTO)
            assertEquals(Direction.FORWARDS, view.playingAnimations.first().direction)
            view.play("one", direction = Direction.BACKWARDS)
            assertEquals(Direction.BACKWARDS, view.playingAnimations.first().direction)
            view.play("one", direction = Direction.AUTO)
            assertEquals(Direction.BACKWARDS, view.playingAnimations.first().direction)
            view.pause("one")

            // PingPong cycles between forwards and backwards
            view.play("two", loop = Loop.PINGPONG)
            assertEquals(Direction.FORWARDS, view.playingAnimations.first().direction)
            view.renderer.advance(1001f)
            assertEquals(Direction.BACKWARDS, view.playingAnimations.first().direction)

        }
    }


    @Test
    fun viewSetResourceLoadArtboard() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)

            view.setRiveResource(R.raw.multiple_animations)
            assertEquals(
                listOf("four", "three", "two", "one"),
                view.renderer.file?.firstArtboard?.animationNames
            )

            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(
                listOf("artboard2animation1", "artboard2animation2"),
                view.renderer.file?.firstArtboard?.animationNames
            )
        }
    }

    @Test
    fun viewSetResourceLoadArtboardArtboardGotcha() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)

            view.setRiveResource(R.raw.multiple_animations, artboardName = "New Artboard")
            view.setRiveResource(R.raw.multipleartboards)
        }
    }

    @Test
    fun viewSetResourceLoadArtboardArtboardGotchaOK() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)

            view.setRiveResource(R.raw.multiple_animations, artboardName = "New Artboard")
            view.setRiveResource(R.raw.multipleartboards, artboardName = "artboard1")
        }
    }


    @Test
    fun viewStop() {
        UiThreadStatement.runOnUiThread {

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(true, view.isPlaying)
            assertEquals(1, view.animations.size)
            assertEquals(1, view.playingAnimations.size)
            view.stop()

            assertEquals(0, view.animations.size)
            assertEquals(0, view.playingAnimations.size)
            assertEquals(false, view.isPlaying)
        }
    }

    @Test
    fun viewStopMultiple() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)
            view.play(listOf("one", "two", "three", "four"))
            assertEquals(true, view.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                view.playingAnimations.map { it.animation.name }.toHashSet(),
            )
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                view.animations.map { it.animation.name }.toHashSet(),
            )

            view.stop(listOf("one", "three"))
            assertEquals(true, view.isPlaying)
            assertEquals(
                hashSetOf("two", "four"),
                view.playingAnimations.map { it.animation.name }.toHashSet(),
            )
            assertEquals(
                hashSetOf("two", "four"),
                view.animations.map { it.animation.name }.toHashSet(),
            )

            view.stop(listOf("two", "four"))
            assertEquals(false, view.isPlaying)
            assertEquals(
                hashSetOf<String>(),
                view.playingAnimations.map { it.animation.name }.toHashSet(),
            )
            assertEquals(
                hashSetOf<String>(),
                view.animations.map { it.animation.name }.toHashSet(),
            )
        }
    }

    @Test
    fun viewStopOneByOne() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)
            view.play(listOf("one", "two", "three", "four"))

            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                view.animations.map { it.animation.name }.toHashSet()
            )
            view.stop("junk")
            assertEquals(true, view.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                view.animations.map { it.animation.name }.toHashSet()
            )

            view.stop("one")
            assertEquals(true, view.isPlaying)
            assertEquals(
                hashSetOf("two", "three", "four"),
                view.animations.map { it.animation.name }.toHashSet()
            )

            view.stop("two")
            assertEquals(true, view.isPlaying)

            view.stop("three")
            assertEquals(true, view.isPlaying)

            view.stop("four")
            assertEquals(false, view.isPlaying)

            assertEquals(
                hashSetOf<LinearAnimationInstance>(),
                view.animations.map { it.animation.name }.toHashSet()
            )
        }
    }

    @Test
    fun viewStopAnimationDetailsTime() {
        UiThreadStatement.runOnUiThread {

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)

            view.play("one", Loop.PINGPONG)
            view.renderer.advance(0.1f)

            assertEquals(0.1f, view.animations.first().time)
            view.stop("one")
            view.play("one")
            assertEquals(0f, view.animations.first().time)
            assertEquals(Loop.ONESHOT, view.animations.first().loop)
        }
    }

    @Test
    fun viewPauseAnimationDetailsTime() {
        UiThreadStatement.runOnUiThread {

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)

            view.play("one", Loop.PINGPONG)
            view.renderer.advance(0.1f)

            assertEquals(0.1f, view.animations.first().time)
            view.pause("one")
            view.play("one")
            assertEquals(0.1f, view.animations.first().time)
            assertEquals(Loop.PINGPONG, view.animations.first().loop)

        }
    }

    @Test
    fun viewReset() {
        // pretty basic test. we could start seeing if the artboards properties are reset properly
        // but we actually would need to expose a lot more of that to do this.
        UiThreadStatement.runOnUiThread {

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)

            view.play("one", Loop.PINGPONG)
            val originalPointer = view.renderer.activeArtboard?.cppPointer
            view.reset()
            assertNotEquals(view.renderer.activeArtboard?.cppPointer, originalPointer)
            assertEquals(false, view.isPlaying)

        }
    }

    @Test
    fun viewResetAutoplay() {
        UiThreadStatement.runOnUiThread {

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = true)
            assertEquals(true, view.isPlaying)
            val originalPointer = view.renderer.activeArtboard?.cppPointer
            view.reset()
            assertNotEquals(view.renderer.activeArtboard?.cppPointer, originalPointer)
            assertEquals(true, view.isPlaying)
        }
    }
}