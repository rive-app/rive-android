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
    var plays = mutableListOf<PlayableInstance>()
    var pauses = mutableListOf<PlayableInstance>()
    var stops = mutableListOf<PlayableInstance>()
    var loops = mutableListOf<PlayableInstance>()
    var states = mutableListOf<LayerState>()
    override fun notifyPlay(playableInstance: PlayableInstance) {
        plays.add(playableInstance)
    }

    override fun notifyPause(playableInstance: PlayableInstance) {
        pauses.add(playableInstance)
    }

    override fun notifyStop(playableInstance: PlayableInstance) {
        stops.add(playableInstance)
    }

    override fun notifyLoop(playableInstance: PlayableInstance) {
        loops.add(playableInstance)
    }
    override fun notifyStateChanged(state: LayerState) {
        states.add(state)
    }
}

@RunWith(AndroidJUnit4::class)
class RiveEventTest {

    @Test
    fun testRegisterOrder() {
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

    @Test
    fun testStateMachineLayerStates() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val observer = Observer()

            val view = RiveAnimationView(appContext)
            view.registerListener(observer)
            view.setRiveResource(R.raw.what_a_state, stateMachineName = "State Machine 2")
            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].isAnimationState)
            view.drawable.advance(2000f)
            assertEquals(2, observer.states.size)
            assertEquals(true, observer.states[1].isExitState)
        }
    }

    @Test
    fun testStateMachineLayerStatesAnimations() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val observer = Observer()

            val view = RiveAnimationView(appContext)
            view.registerListener(observer)
            view.setRiveResource(R.raw.what_a_state, stateMachineName = "State Machine 1")
            assertEquals(0, observer.states.size)

            view.fireState("State Machine 1", "right")

            // lets just start, expect 1 change.
            view.drawable.advance(400f)
            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].isAnimationState)
            assertEquals("go right", (observer.states[0] as AnimationState).animation.name)
            observer.states.clear()

            // should be in same animation still. no state change
            view.drawable.advance(400f)
            assertEquals(0, observer.states.size)
            assertEquals(true, view.isPlaying)

            // animation came to an end inside this time period, this still means no state change
            view.drawable.advance(400f)
            assertEquals(false, view.isPlaying)
            assertEquals(0, observer.states.size)

            // animation is just kinda stuck there. no change no happening.
            view.drawable.advance(400f)
            assertEquals(false, view.isPlaying)
            assertEquals(0, observer.states.size)

            // ok lets change thigns up again.
            view.fireState("State Machine 1", "change")
            view.drawable.advance(400f)
            assertEquals(true, view.isPlaying)
            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].isAnimationState)
            assertEquals("change!", (observer.states[0] as AnimationState).animation.name)
            observer.states.clear()

            // as before lets advance inside the animation -> no change
            view.drawable.advance(400f)
            assertEquals(true, view.isPlaying)
            assertEquals(0, observer.states.size)

            // as before lets advance beyond the end of the animaiton, in this case change to exit!
            view.drawable.advance(400f)
            assertEquals(false, view.isPlaying)
            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].isExitState)
            observer.states.clear()

            // chill on exit. no change.
            view.drawable.advance(400f)
            assertEquals(false, view.isPlaying)
            assertEquals(0, observer.states.size)
        }
    }

    @Test
    fun testStateMachineLayerStatesAnimationsDoubleChange() {
        UiThreadStatement.runOnUiThread {
            val appContext = initTests()
            val observer = Observer()

            val view = RiveAnimationView(appContext)
            view.registerListener(observer)
            view.setRiveResource(R.raw.what_a_state, stateMachineName = "State Machine 1")
            assertEquals(0, observer.states.size)

            view.fireState("State Machine 1", "change")
            // lets just start, expect 1 change.
            view.drawable.advance(1200f)
            assertEquals(2, observer.states.size)
            assertEquals(true, observer.states[0].isAnimationState)
            assertEquals("change!", (observer.states[0] as AnimationState).animation.name)
            assertEquals(true, observer.states[1].isExitState)
        }
    }

}