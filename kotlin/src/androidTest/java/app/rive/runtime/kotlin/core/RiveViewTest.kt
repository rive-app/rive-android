package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveViewTest {

    @Test
    fun viewNoDefaults() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)

            assertEquals(view.isPlaying, false)
        }
    }

    @Test
    fun viewDefaultsLoadResouce() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards)

            assertEquals(view.isPlaying, true)
            assertEquals(view.file?.artboardNames, listOf("artboard2", "artboard1"))
            assertEquals(
                view.animations.map { it.animation.name }.toList(),
                listOf("artboard2animation1", "artboard2animation2")
            )
        }
    }

    @Test
    fun viewDefaultsChangeArtboard() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(view.isPlaying, true)
            view.artboardName = "artboard1"
            assertEquals(
                view.animations.map { it.animation.name }.toList(),
                listOf("artboard1animation1")
            )
        }

    }

    @Test
    fun viewDefaultsNoAutoplay() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()

            val view = RiveAnimationView(appContext)
            view.autoplay = false
            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(view.isPlaying, false)
            view.artboardName = "artboard2"
            assertEquals(
                view.animations.map { it.animation.name }.toList(),
                listOf<String>()
            )
            view.play(listOf("artboard2animation1", "artboard2animation2"))
            assertEquals(
                view.animations.map { it.animation.name }.toList(),
                listOf("artboard2animation1", "artboard2animation2")
            )
        }
    }

    @Test
    fun viewPause() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(view.isPlaying, true)
            assertEquals(view.animations.size, 2)
            assertEquals(view.playingAnimations.size, 2)
            view.pause()
            assertEquals(view.isPlaying, false)
            assertEquals(view.animations.size, 2)
            assertEquals(view.playingAnimations.size, 0)
        }
    }


    @Test
    fun viewPauseOneByOne() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations)

            assertEquals(view.isPlaying, true)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("one", "two", "three", "four")
            )
            view.pause("junk")
            assertEquals(view.isPlaying, true)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("one", "two", "three", "four")
            )

            view.pause("one")
            assertEquals(view.isPlaying, true)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("two", "three", "four")
            )
            view.pause("two")
            assertEquals(view.isPlaying, true)

            view.pause("three")
            assertEquals(view.isPlaying, true)

            view.pause("four")
            assertEquals(view.isPlaying, false)

            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf<LinearAnimationInstance>()
            )
        }
    }

    @Test
    fun viewPauseMultiple() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations)
            assertEquals(view.isPlaying, true)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("one", "two", "three", "four")
            )

            view.pause(listOf("one", "three"))
            assertEquals(view.isPlaying, true)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("two", "four")
            )

            view.pause(listOf("two", "four"))
            assertEquals(view.isPlaying, false)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf<LinearAnimationInstance>()
            )
        }
    }

    @Test
    fun viewPlay() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards, autoplay = false)
            assertEquals(view.isPlaying, false)
            assertEquals(view.animations.size, 0)
            assertEquals(view.playingAnimations.size, 0)
            view.play()
            assertEquals(view.isPlaying, true)
            assertEquals(view.animations.size, 2)
            assertEquals(view.playingAnimations.size, 2)
        }
    }

    @Test
    fun viewPlayOneByOne() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assertEquals(view.isPlaying, false)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf<LinearAnimationInstance>()
            )
            view.play("one")
            assertEquals(view.isPlaying, true)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("one")
            )
        }
    }

    @Test(expected = RiveException::class)
    fun viewPlayJunk() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assertEquals(view.isPlaying, false)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf<LinearAnimationInstance>()
            )
            view.play("junk")
        }
    }

    @Test
    fun viewPlayMultiple() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assertEquals(false, view.isPlaying)
            assertEquals(
                hashSetOf<LinearAnimationInstance>(),
                view.playingAnimations.map { it.animation.name }.toHashSet(),

                )
            view.play(listOf("one", "two"))
            assertEquals(true, view.isPlaying)
            assertEquals(
                hashSetOf("one", "two"),
                view.playingAnimations.map { it.animation.name }.toHashSet(),

                )
        }
    }

    @Test
    fun viewPlayLoopMode() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)

            // Mode dictated by animation.
            view.play("one")
            assertEquals(view.playingAnimations.first().animation.loop, Loop.ONESHOT)

            // forced loop
            view.play("one", Loop.LOOP)
            assertEquals(view.playingAnimations.first().animation.loop, Loop.LOOP)

            // mode unchanged.
            view.play("one")
            assertEquals(view.playingAnimations.first().animation.loop, Loop.LOOP)

            // mode unchanged.
            view.play("one", Loop.PINGPONG)
            assertEquals(view.playingAnimations.first().animation.loop, Loop.PINGPONG)

            // mode unchanged.
            view.play("one", Loop.ONESHOT)
            assertEquals(view.playingAnimations.first().animation.loop, Loop.ONESHOT)
        }
    }

    @Test
    fun viewPlayDirection() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
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
            view.drawable.advance(1001f)
            assertEquals(Direction.BACKWARDS, view.playingAnimations.first().direction)

        }
    }


    @Test
    fun viewSetResourceLoadArtboard() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)

            view.setRiveResource(R.raw.multiple_animations)
            assertEquals(
                listOf("four", "three", "two", "one"),
                view.drawable.file?.firstArtboard?.animationNames
            )

            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(
                listOf("artboard2animation1", "artboard2animation2"),
                view.drawable.file?.firstArtboard?.animationNames
            )
        }
    }

    @Test(expected = RiveException::class)
    fun viewSetResourceLoadArtboardArtboardGotcha() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)

            view.setRiveResource(R.raw.multiple_animations, artboardName = "New Artboard")
            view.setRiveResource(R.raw.multipleartboards)
        }
    }

    @Test
    fun viewSetResourceLoadArtboardArtboardGotchaOK() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)

            view.setRiveResource(R.raw.multiple_animations, artboardName = "New Artboard")
            view.setRiveResource(R.raw.multipleartboards, artboardName = "artboard1")
        }
    }


    @Test
    fun viewStop() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(true, view.isPlaying)
            assertEquals(2, view.animations.size)
            assertEquals(2, view.playingAnimations.size)
            view.stop()

            assertEquals(0, view.animations.size)
            assertEquals(0, view.playingAnimations.size)
            assertEquals(false, view.isPlaying)
        }
    }

    @Test
    fun viewStopMultiple() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations)
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
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations)

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
    @Ignore
    fun viewStopAnimationDetailsTime() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)

            view.play("one", Loop.PINGPONG)
            view.drawable.advance(100f)

            assertEquals(0.1f, view.animations.first().time)
            view.stop("one")
            view.play("one")
            assertEquals(0f, view.animations.first().time)
            assertEquals(Loop.ONESHOT, view.animations.first().animation.loop)

        }
    }

    @Test
    fun viewPauseAnimationDetailsTime() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multiple_animations, autoplay = false)

            view.play("one", Loop.PINGPONG)
            view.drawable.advance(100f)

            assertEquals(0.1f, view.animations.first().time)
            view.pause("one")
            view.play("one")
            assertEquals(0.1f, view.animations.first().time)
            assertEquals(Loop.PINGPONG, view.animations.first().animation.loop)

        }
    }
}