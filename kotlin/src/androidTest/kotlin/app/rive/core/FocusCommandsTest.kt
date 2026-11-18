package app.rive.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveAndroidTest
import app.rive.RiveFocusDirection
import app.rive.RiveFocusState
import app.rive.RiveStateMachineException
import app.rive.runtime.kotlin.test.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith

/**
 * Exercises the focus commands against the real command server.
 */
@RunWith(AndroidJUnit4::class)
class FocusCommandsTest : RiveAndroidTest() {
    @Test
    fun requestHasFocusNodes_reportsAuthoredFocusContent() = runBlocking {
        val resources = loadDefaultRiveResources(R.raw.focus_nodes_list_order)
        val stateMachine = resources.stateMachine

        stateMachine.requestHasFocusNodes()

        withTimeout(RESPONSE_TIMEOUT) {
            assertTrue(stateMachine.hasFocusNodes.first { it })
        }
    }

    @Test
    fun moveFocus_takesFocusAndClearFocusReleasesIt(): Unit = runBlocking {
        val resources = loadDefaultRiveResources(R.raw.focus_nodes_list_order)
        val stateMachine = resources.stateMachine
        // Traversal drops focus targets that are still hidden, so settle the instance's visibility
        // before moving focus.
        stateMachine.advance(ZERO)

        val entered =
            withTimeout(RESPONSE_TIMEOUT) { stateMachine.moveFocus(RiveFocusDirection.Next) }

        assertTrue(entered.moved)
        assertIs<RiveFocusState.Focused>(entered.focusState)
        assertEquals(entered.focusState, stateMachine.focusState.value)

        // No explicit focus-state poll: a focus-changing command requests the state it produces.
        stateMachine.clearFocus()

        withTimeout(RESPONSE_TIMEOUT) {
            stateMachine.focusState.first { it == RiveFocusState.Unfocused }
        }
    }

    @Test
    fun moveFocus_runningOffTheEndReportsNoMove(): Unit = runBlocking {
        val stateMachine = loadDefaultRiveResources(R.raw.focus_nodes_list_order).stateMachine
        stateMachine.advance(ZERO)
        withTimeout(RESPONSE_TIMEOUT) { stateMachine.moveFocus(RiveFocusDirection.Next) }

        // Previous from the first node runs off the end.
        val ranOff = withTimeout(RESPONSE_TIMEOUT) {
            stateMachine.moveFocus(RiveFocusDirection.Previous)
        }

        assertFalse(ranOff.moved)
        assertEquals(RiveFocusState.Unfocused, ranOff.focusState)
    }

    @Test
    fun moveFocus_rightAndLeftMoveBetweenTheButtons(): Unit = runBlocking {
        val stateMachine = loadDefaultRiveResources(R.raw.focus_buttons_row).stateMachine
        stateMachine.advance(ZERO)
        withTimeout(RESPONSE_TIMEOUT) { stateMachine.moveFocus(RiveFocusDirection.Next) }

        val right = withTimeout(RESPONSE_TIMEOUT) {
            stateMachine.moveFocus(RiveFocusDirection.Right)
        }
        assertTrue(right.moved)
        assertIs<RiveFocusState.Focused>(right.focusState)

        val left = withTimeout(RESPONSE_TIMEOUT) { stateMachine.moveFocus(RiveFocusDirection.Left) }
        assertTrue(left.moved)

        // Previous runs off the end only from the first button, so Left went back to it.
        val previous = withTimeout(RESPONSE_TIMEOUT) {
            stateMachine.moveFocus(RiveFocusDirection.Previous)
        }
        assertFalse(previous.moved)
        assertEquals(RiveFocusState.Unfocused, previous.focusState)
    }

    @Test
    fun moveFocus_anArrowWithNothingThatWayKeepsFocus(): Unit = runBlocking {
        val stateMachine = loadDefaultRiveResources(R.raw.focus_buttons_row).stateMachine
        stateMachine.advance(ZERO)
        withTimeout(RESPONSE_TIMEOUT) { stateMachine.moveFocus(RiveFocusDirection.Next) }

        // Nothing lies above the row.
        val up = withTimeout(RESPONSE_TIMEOUT) { stateMachine.moveFocus(RiveFocusDirection.Up) }

        assertFalse(up.moved)
        assertIs<RiveFocusState.Focused>(up.focusState)
    }

    @Test
    fun moveFocus_anArrowCannotStartFromNothing(): Unit = runBlocking {
        val stateMachine = loadDefaultRiveResources(R.raw.focus_buttons_row).stateMachine
        stateMachine.advance(ZERO)

        // Down, which no other test here moves in.
        val down = withTimeout(RESPONSE_TIMEOUT) { stateMachine.moveFocus(RiveFocusDirection.Down) }

        assertFalse(down.moved)
        assertEquals(RiveFocusState.Unfocused, down.focusState)
    }

    @Test
    fun moveFocus_onAnUnknownStateMachineFails(): Unit = runBlocking {
        // A handle the command server never created.
        val unknown = StateMachineHandle(Long.MAX_VALUE)

        assertFailsWith<RiveStateMachineException> {
            withTimeout(RESPONSE_TIMEOUT) { riveWorker.moveFocus(unknown, RiveFocusDirection.Next) }
        }
    }

    companion object {
        private val RESPONSE_TIMEOUT = 5.seconds
    }
}
