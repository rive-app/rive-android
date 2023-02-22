package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveStateMachineStateResolutionTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var mockView: RiveAnimationView

    /*
    State Machine overview:

    Inputs:
    choice - number = default 3
    jump - trigger

    Map:
    entry -> choice 1 -> jump 1
          -> choice 2 -> jump 2
          -> choice 3 -> choice 3 part 2
     */

    @Before
    fun init() {
        mockView = TestUtils.MockNoopRiveAnimationView(appContext)
    }

    @Test
    fun autoPlayTriggersStateResolution() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.state_machine_state_resolution,
                stateMachineName = "StateResolution",
                autoplay = true
            )
            assertEquals(1, observer.states.size)
        }
    }

    @Test
    fun disablingAutoplaySuppressesStateResolution() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.state_machine_state_resolution,
                stateMachineName = "StateResolution",
                autoplay = false
            )
            assertEquals(0, observer.states.size)
        }
    }

    @Test
    fun explicitPlayTriggersStateResolution() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.state_machine_state_resolution,
                stateMachineName = "StateResolution",
                autoplay = false
            )

            mockView.play("StateResolution", isStateMachine = true)
            assertEquals(1, observer.states.size)

            assertEquals("StateResolution", observer.states[0].stateMachineName)
            assertEquals("Choice 3 part 2", observer.states[0].stateName)
        }
    }

    @Test
    fun explicitPlayCanSuppressTriggeringStateResolution() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.state_machine_state_resolution,
                stateMachineName = "StateResolution",
                autoplay = false
            )
            mockView.play(
                "StateResolution",
                isStateMachine = true,
                settleInitialState = false
            )
            assertEquals(0, observer.states.size)
        }
    }

    @Test
    fun canAlterInitialStateBeforePlay() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.state_machine_state_resolution,
                stateMachineName = "StateResolution",
                autoplay = false
            )
            mockView.setNumberState("StateResolution", "Choice", 1f)
            mockView.play(
                "StateResolution",
                isStateMachine = true,
            )
            assertEquals(1, observer.states.size)

            assertEquals("StateResolution", observer.states[0].stateMachineName)
            assertEquals("Choice 1", observer.states[0].stateName)
        }
    }


    @Test
    fun getUnknownStateNameWhenAnimationIsMissing() {
//        This test will crash the tester when it does not work
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.empty_animation_state,
                autoplay = false
            )
            mockView.play(
                "State Machine 1",
                isStateMachine = true,
            )
            assertEquals(1, observer.states.size)
            assertEquals("Unknown", observer.states[0].stateName)
        }
    }

    @Test
    fun triggerDoesNothingWithoutStateResolution() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.state_machine_state_resolution,
                stateMachineName = "StateResolution",
                autoplay = false
            )
            mockView.setNumberState("StateResolution", "Choice", 2f)
            mockView.play(
                "StateResolution",
                isStateMachine = true,
                settleInitialState = false
            )
            mockView.fireState("StateResolution", "Jump")
            // trigger is registered, but it wouldn't be picked up until we evaluate the state next.
            mockView.renderer.advance(0f)

            assertEquals("StateResolution", observer.states[0].stateMachineName)
            assertEquals("Choice 2", observer.states[0].stateName)
        }
    }

    @Test
    fun triggerFiresWithWithStateResolution() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.state_machine_state_resolution,
                stateMachineName = "StateResolution",
                autoplay = false
            )
            mockView.setNumberState("StateResolution", "Choice", 2f)
            mockView.play(
                "StateResolution",
                isStateMachine = true,
                settleInitialState = true
            )
            mockView.fireState("StateResolution", "Jump")
            // trigger is registered, but it wont be picked up until we evaluate the state next.
            mockView.renderer.advance(0f)

            assertEquals(2, observer.states.size)

            assertEquals("StateResolution", observer.states[0].stateMachineName)
            assertEquals("Choice 2", observer.states[0].stateName)

            assertEquals("StateResolution", observer.states[1].stateMachineName)
            assertEquals("Jump 2", observer.states[1].stateName)
        }
    }

}