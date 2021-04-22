package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.RiveDrawable
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith


class Observer : RiveDrawable.Listener {
    var plays = mutableListOf<LinearAnimationInstance>()
    var pauses = mutableListOf<LinearAnimationInstance>()
    var stops = mutableListOf<LinearAnimationInstance>()
    var loops = mutableListOf<LinearAnimationInstance>()
    override fun notifyPlay(animation: LinearAnimationInstance) {
        plays.add(animation)
    }

    override fun notifyPause(animation: LinearAnimationInstance) {
        pauses.add(animation)
    }

    override fun notifyStop(animation: LinearAnimationInstance) {
        stops.add(animation)
    }

    override fun notifyLoop(animation: LinearAnimationInstance) {
        loops.add(animation)
    }
}

@RunWith(AndroidJUnit4::class)
class RiveEventTest {

    @Test
    @Ignore
    fun testRegisterOrder() {
        // doesn't work right now, because we trash the drawable
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            val observer = Observer()
            view.autoplay = false

            view.registerListener(observer)
            view.setRiveResource(R.raw.multiple_animations)
            view.play("one")
            assertEquals(1, observer.plays.size)
        }
    }

    @Test
    fun testPlayEvent() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            val observer = Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.play("one")
            assertEquals(1, observer.plays.size)
        }
    }

    @Test
    fun testPlayEventAlreadyPlaying() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            val observer = Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.play("one")
            view.play("one")
            // we trigger play everytime the play request is triggered.
            // as this could be changing playback modes/direction
            assertEquals(2, observer.plays.size)
        }
    }

    @Test
    fun testPauseEvent() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            val observer = Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.play("one")
            view.pause("one")
            assertEquals(1, observer.pauses.size)
        }
    }


    @Test
    fun testPauseEventNotPlaying() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            val observer = Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.pause("one")
            assertEquals(0, observer.pauses.size)
        }
    }


    @Test
    fun testStopEvent() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            val observer = Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.play("one")
            view.stop("one")
            assertEquals(1, observer.stops.size)
        }
    }


    @Test
    fun testStopEventNotPlaying() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            val observer = Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.stop("one")
            assertEquals(0, observer.stops.size)
        }
    }

    @Test
    fun testLoopOneshot() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            val observer = Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.play("one", Loop.ONESHOT)

            view.drawable.advance(
                view.drawable.animations.first().animation.effectiveDurationInSeconds * 1000 + 1
            )

            assertEquals(1, observer.plays.size)
            assertEquals(0, observer.pauses.size)
            assertEquals(1, observer.stops.size)
            assertEquals(0, observer.loops.size)
        }
    }

    @Test
    fun testLoopLoop() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            val observer = Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.play("one", Loop.LOOP)

            view.drawable.advance(
                view.drawable.animations.first().animation.effectiveDurationInSeconds * 1000
            )

            assertEquals(1, observer.plays.size)
            assertEquals(0, observer.pauses.size)
            assertEquals(0, observer.stops.size)
            assertEquals(1, observer.loops.size)
        }
    }

    @Test
    fun testLoopPingPong() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val view = RiveAnimationView(appContext)
            val observer = Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.play("one", Loop.PINGPONG)

            view.drawable.advance(
                view.drawable.animations.first().animation.effectiveDurationInSeconds * 1000
            )

            assertEquals(1, observer.plays.size)
            assertEquals(0, observer.pauses.size)
            assertEquals(0, observer.stops.size)
            assertEquals(1, observer.loops.size)
        }
    }

}