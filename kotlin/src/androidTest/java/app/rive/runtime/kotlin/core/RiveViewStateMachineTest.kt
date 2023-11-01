package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveViewStateMachineTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var mockView: RiveAnimationView

    @Before
    fun initView() {
        mockView = TestUtils.MockRiveAnimationView(appContext)
    }

    @Test
    fun viewDefaultsLoadResource() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_state_machines, autoplay = false)
            mockView.play(listOf("one", "two"), areStateMachines = true)

            assertEquals(true, mockView.isPlaying)
            assertEquals(listOf("New Artboard"), mockView.file?.artboardNames)
            assertEquals(
                listOf("one", "two"),
                mockView.stateMachines.map { it.name }.toList()
            )
        }
    }

    @Test
    fun viewDefaultsNoAutoplay() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_state_machines, autoplay = false)
            assertEquals(false, mockView.isPlaying)
            mockView.artboardName = "New Artboard"
            assertEquals(
                listOf<String>(),
                mockView.stateMachines.map { it.name }.toList()
            )
        }
    }

    @Test
    fun viewPause() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_state_machines, stateMachineName = "one")
            assertEquals(true, mockView.isPlaying)
            assertEquals(0, mockView.animations.size)
            assertEquals(0, mockView.playingAnimations.size)
            assertEquals(1, mockView.stateMachines.size)
            assertEquals(1, mockView.playingStateMachines.size)
            mockView.pause()
            assertFalse(mockView.isPlaying)
            assertEquals(1, mockView.stateMachines.size)
            assertEquals(0, mockView.playingStateMachines.size)
        }
    }

    @Test
    fun viewPlayStateWithNoDuration() {
        UiThreadStatement.runOnUiThread {
            // state machine four's has transitions that happen instantly, so we do not stick on
            // a state that's playing an animation
            mockView.setRiveResource(R.raw.multiple_state_machines, stateMachineName = "four")
            assertEquals(false, mockView.isPlaying)
            assertEquals(1, mockView.stateMachines.size)
            assertEquals(0, mockView.playingStateMachines.size)
        }
    }

    @Test
    fun viewStateMachinesPause() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.what_a_state, stateMachineName = "State Machine 2")
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Let the state machine animation run its course.
            mockView.artboardRenderer!!.advance(1.01f)
            assert(!mockView.isPlaying)
        }
    }

    @Test
    fun viewStateMachinePlayBeforeAttach() {
        UiThreadStatement.runOnUiThread {
            val mView = mockView as TestUtils.MockRiveAnimationView
            val controller = mView.controller
            mView.setRiveResource(R.raw.what_a_state)
            mView.play("State Machine 2", isStateMachine = true)
            assertEquals(1, controller.stateMachines.size)
            // Scroll away: remove the view.
            mView.mockDetach()
            assertFalse(controller.isActive)
            assertNull(controller.file)

            // Scroll back, re-add the view.
            mView.mockAttach()
            assertTrue(controller.isActive)
            assertEquals("State Machine 2", controller.stateMachines.first().name)
        }
    }

    @Test
    @Ignore("We're not stopping state machines when all layers are stopped atm.")
    fun viewStateMachinesInstancesRemoveOnStop() {
        UiThreadStatement.runOnUiThread {

            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.what_a_state, stateMachineName = "State Machine 2")

            assert(view.artboardRenderer != null)
            val renderer = view.artboardRenderer!!
            assertEquals(1, view.stateMachines.size)
            renderer.advance(2f)
            assertEquals(false, view.isPlaying)
            assertEquals(0, view.stateMachines.size)
        }
    }

}