package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

            assertTrue(mockView.isPlaying)
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
            assertFalse(mockView.isPlaying)
            mockView.artboardName = "New Artboard"
            assertEquals(
                emptyList<String>(),
                mockView.stateMachines.map { it.name }.toList()
            )
        }
    }

    @Test
    fun viewPause() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_state_machines, stateMachineName = "one")
            assertTrue(mockView.isPlaying)
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
            // State machine four has transitions that happen instantly
            // which when auto-played will settle immediately.
            mockView.setRiveResource(R.raw.multiple_state_machines, stateMachineName = "four")
            assertTrue(mockView.isPlaying)
            assertEquals(1, mockView.stateMachines.size)
            assertEquals(1, mockView.playingStateMachines.size)
            mockView.artboardRenderer?.advance(0.016f)
            assertFalse(mockView.isPlaying)
            assertEquals(1, mockView.stateMachines.size)
            assertEquals(0, mockView.playingStateMachines.size)
        }
    }

    @Test
    fun viewStateMachinesPause() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.what_a_state, stateMachineName = "State Machine 2")
            assertTrue(mockView.isPlaying)
            assertNotNull(mockView.artboardRenderer)
            // Let the state machine animation run its course.
            mockView.artboardRenderer!!.advance(1.01f)
            // Must advance by non 0 to truly complete.
            mockView.artboardRenderer!!.advance(0.01f)
            assertFalse(mockView.isPlaying)
        }
    }

    @Test
    fun viewStateMachinePlayBeforeAttach() {
        UiThreadStatement.runOnUiThread {
            val view = mockView as TestUtils.MockRiveAnimationView
            val controller = view.controller
            view.setRiveResource(R.raw.what_a_state)

            view.play("State Machine 2", isStateMachine = true)
            assertEquals(1, controller.stateMachines.size)

            // Scroll away: remove the view.
            view.mockDetach()
            assertFalse(controller.isActive)
            assertNull(controller.file)

            // Scroll back, re-add the view.
            view.mockAttach()
            assertTrue(controller.isActive)
            assertEquals("State Machine 2", controller.stateMachines.first().name)
        }
    }

    @Test
    fun nestedStateMachinesContinuePlaying() {
        UiThreadStatement.runOnUiThread {
            // The main state machine's controller is not advancing, however, nested artboards
            // need to continue playing.
            mockView.setRiveResource(R.raw.nested_settle)

            mockView.play("State Machine 1", isStateMachine = true)
            assertEquals(1, mockView.controller.stateMachines.size)

            mockView.artboardRenderer?.advance(0.5f)
            assertTrue(mockView.isPlaying)
            mockView.artboardRenderer?.advance(0.5f)
            assertTrue(mockView.isPlaying)

            // The nested artboard should now be settled
            mockView.artboardRenderer?.advance(0.01f)
            assertFalse(mockView.isPlaying)
        }
    }

    @Test
    @Ignore("We're not stopping state machines when all layers are stopped atm.")
    fun viewStateMachinesInstancesRemoveOnStop() {
        UiThreadStatement.runOnUiThread {
            val view = RiveAnimationView(appContext)
            view.setRiveResource(R.raw.what_a_state, stateMachineName = "State Machine 2")
            assertEquals(1, view.stateMachines.size)

            val renderer = view.artboardRenderer!!
            renderer.advance(2f)
            assertFalse(view.isPlaying)
            assertEquals(0, view.stateMachines.size)
        }
    }
}
