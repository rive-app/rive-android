package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveEventTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var view: TestUtils.MockRiveAnimationView

    @Before
    fun init() {
        view = TestUtils.MockRiveAnimationView(appContext)
    }

    @Test
    fun testRegisterOrder() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
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
            val observer = TestUtils.Observer()
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
            val observer = TestUtils.Observer()
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
            val observer = TestUtils.Observer()
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
            val observer = TestUtils.Observer()
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
            val observer = TestUtils.Observer()
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
            val observer = TestUtils.Observer()
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
            val observer = TestUtils.Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.play("one", Loop.ONESHOT)

            view.renderer.advance(
                view.renderer.animations.first().effectiveDurationInSeconds + 1
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
            val observer = TestUtils.Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.play("one", Loop.LOOP)

            view.renderer.advance(
                view.renderer.animations.first().effectiveDurationInSeconds
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
            val observer = TestUtils.Observer()
            view.autoplay = false

            view.setRiveResource(R.raw.multiple_animations)
            view.registerListener(observer)

            view.play("one", Loop.PINGPONG)

            view.renderer.advance(
                view.renderer.animations.first().effectiveDurationInSeconds
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
            val observer = TestUtils.Observer()

            view.registerListener(observer)
            view.setRiveResource(R.raw.what_a_state, stateMachineName = "State Machine 2")
            assertEquals(observer.states.size, 1)
            view.renderer.advance(0f)
            assertEquals(true, observer.states[0].stateMachineName == "State Machine 2")
            assertEquals(true, observer.states[0].stateName == "go right")
            view.renderer.advance(2f)
            view.renderer.advance(2f)
            assertEquals(2, observer.states.size)
            assertEquals(true, observer.states[1].stateName == "ExitState")
        }
    }

    @Test
    fun testStateMachineLayerStatesAnimations() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()

            view.registerListener(observer)
            view.setRiveResource(R.raw.what_a_state, stateMachineName = "State Machine 1")
            assertEquals(0, observer.states.size)

            view.fireState("State Machine 1", "right")

            // lets just start, expect 1 change.
            view.renderer.advance(0.4f)
            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].stateName == "go right")
            observer.states.clear()

            // should be in same animation still. no state change
            assertEquals(true, view.isPlaying)
            view.renderer.advance(0.4f)
            assertEquals(0, observer.states.size)
            assertEquals(true, view.isPlaying)

            // animation came to an end inside this time period, this still means no state change
            view.renderer.advance(1.0f)
            assertEquals(false, view.isPlaying)
            assertEquals(0, observer.states.size)

            // animation is just kinda stuck there. no change no happening.
            view.renderer.advance(0.4f)
            assertEquals(false, view.isPlaying)
            assertEquals(0, observer.states.size)

            // ok lets change things up again.
            view.fireState("State Machine 1", "change")
            view.renderer.advance(0.4f)
            assertEquals(true, view.isPlaying)
            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].stateName == "change!")
            observer.states.clear()

            // as before lets advance inside the animation -> no change
            view.renderer.advance(0.4f)
            assertEquals(true, view.isPlaying)
            assertEquals(0, observer.states.size)

            // as before lets advance beyond the end of the animation, in this case change to exit!
            view.renderer.advance(1.0f)
            assertEquals(false, view.isPlaying)
            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].stateName == "ExitState")
            observer.states.clear()

            // chill on exit. no change.
            view.renderer.advance(0.4f)
            assertEquals(false, view.isPlaying)
            assertEquals(0, observer.states.size)
        }
    }

    @Test
    fun testStateMachineLayerStatesAnimationsDoubleChange() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()

            view.registerListener(observer)
            view.setRiveResource(R.raw.what_a_state, stateMachineName = "State Machine 1")
            assertEquals(0, observer.states.size)

            view.fireState("State Machine 1", "change")

            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].stateName == "change!")
            view.renderer.advance(1f)
            assertEquals(2, observer.states.size)
            assertEquals(true, observer.states[1].stateName == "ExitState")
        }
    }


    @Test
    fun viewBlendState1DBroken() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()

            view.registerListener(observer)
            view.setRiveResource(R.raw.blend_state, stateMachineName = "one")
            view.fireState("one", "blend mix")

            assertEquals(true, view.isPlaying)
            assertEquals(observer.states.size, 1)
            assertEquals(true, observer.states[0].stateName == "BlendState")
        }
    }

    @Test
    fun viewBlendStateDirectBroken() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()

            view.registerListener(observer)
            view.setRiveResource(R.raw.blend_state, stateMachineName = "one")
            view.fireState("one", "blend other")
            view.renderer.advance(0.0f)

            assertEquals(true, view.isPlaying)
            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].stateName == "BlendState")
        }
    }

    @Test
    fun viewBlendState1D() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()

            view.registerListener(observer)
            view.setRiveResource(R.raw.blend_state, stateMachineName = "two")
            view.fireState("two", "left")
            view.renderer.advance(0.0f)

            // advancing by 0 always returns is playing true
            assertEquals(true, view.isPlaying)
            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].stateName == "BlendState")
        }
    }

    @Test
    fun viewBlendStateDirect() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()

            view.registerListener(observer)
            view.setRiveResource(R.raw.blend_state, stateMachineName = "two")
            view.fireState("two", "right")
            view.renderer.advance(0.0f)

            // advancing by 0 always returns is playing true
            assertEquals(true, view.isPlaying)
            assertEquals(1, observer.states.size)
            assertEquals(true, observer.states[0].stateName == "BlendState")
        }
    }

}