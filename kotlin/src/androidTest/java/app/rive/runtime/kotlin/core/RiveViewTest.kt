package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
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
            view.autoplay=false
            view.setRiveResource(R.raw.multipleartboards)
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
            view.autoplay = false
            view.setRiveResource(R.raw.multiple_animations)
            assertEquals(view.isPlaying, false)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf<LinearAnimationInstance>()
            )
            view.play("one")
            assertEquals(view.isPlaying, true)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("one",  )
            )
        }
    }

    @Test(expected = RiveException::class)
    fun viewPlayJunk() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.autoplay = false
            view.setRiveResource(R.raw.multiple_animations)
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
            view.autoplay = false
            view.setRiveResource(R.raw.multiple_animations)
            assertEquals(view.isPlaying, false)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf<LinearAnimationInstance>()
            )
            view.play(listOf("one", "two"))
            assertEquals(view.isPlaying, true)
            assertEquals(
                view.playingAnimations.map { it.animation.name }.toHashSet(),
                hashSetOf("one", "two", )
            )
        }
    }

    @Test
    fun viewPlayLoopMode() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            view.autoplay = false
            view.setRiveResource(R.raw.multiple_animations)

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
}