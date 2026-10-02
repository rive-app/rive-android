package app.rive.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveAndroidTest
import app.rive.runtime.kotlin.test.R
import kotlin.test.Test
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
    fun focusNext_takesFocusAndClearFocusReleasesIt() = runBlocking {
        val resources = loadDefaultRiveResources(R.raw.focus_nodes_list_order)
        val stateMachine = resources.stateMachine
        // Traversal drops focus targets that are still hidden, so settle the graphic's visibility
        // before moving focus.
        stateMachine.advance(ZERO)

        // No explicit focus-state poll: a focus-changing command requests the state it produces.
        stateMachine.focusNext()

        withTimeout(RESPONSE_TIMEOUT) {
            assertTrue(stateMachine.focusState.first { it.hasFocus }.hasFocus)
        }

        stateMachine.clearFocus()

        withTimeout(RESPONSE_TIMEOUT) {
            assertTrue(!stateMachine.focusState.first { !it.hasFocus }.hasFocus)
        }
    }

    companion object {
        private val RESPONSE_TIMEOUT = 5.seconds
    }
}
