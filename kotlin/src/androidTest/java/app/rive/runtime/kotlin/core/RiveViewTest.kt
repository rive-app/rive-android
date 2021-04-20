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
            view.pause()
            assertEquals(view.isPlaying, false)
        }
    }

    @Test
    fun viewPauseOneByOne() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.multipleartboards)
            assertEquals(view.isPlaying, true)
            assertEquals(
                view.animations.map { it.animation.name }.toList(),
                listOf("artboard2animation1", "artboard2animation2")
            )
            view.pause("artboard2animation1")
            assertEquals(view.isPlaying, true)
            view.pause("artboard2animation2")
            assertEquals(view.isPlaying, false)
        }
    }


}