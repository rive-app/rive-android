package app.rive

import androidx.activity.ComponentActivity
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.compose.rememberTestRiveResources
import app.rive.runtime.kotlin.test.R
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.runner.RunWith

/** Exercises the production focus wiring installed by [Rive]. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RiveFocusComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Written during composition on the main thread, read from the test thread.
    @Volatile
    private var published: StateMachine? = null

    @Volatile
    private var publishedHasFocusNodes: StateFlow<Boolean?>? = null

    @Volatile
    private var stopAboveGraphicFocused = false

    /** Switches [showGraphicWithReplaceableStateMachine] to its replacement state machine. */
    private val useReplacement = mutableStateOf(false)

    @Volatile
    private var stopBelowGraphicFocused = false

    @Volatile
    private var surfaceFocused = false

    @Test
    fun tabEntersTheGraphicThenHandsFocusBackToTheHost() {
        val machine = showGraphic(RiveFocusMode.On)

        enterFocusTree(machine)

        leaveFocusTree(machine, press = ::pressTab)

        // The key that left the tree was consumed, so the host only moves because Rive hands off.
        assertTrue(
            awaitCondition { stopBelowGraphicFocused },
            "leaving forward did not hand focus to the stop below the graphic",
        )
    }

    @Test
    fun shiftTabWalksBackwardOutOfTheGraphic() {
        val machine = showGraphic(RiveFocusMode.On)
        enterFocusTree(machine)
        // Move deeper so backward traversal has somewhere to walk back from.
        pressTab()
        pressTab()

        leaveFocusTree(machine, press = ::pressShiftTab)

        assertTrue(
            awaitCondition { stopAboveGraphicFocused },
            "leaving backward did not hand focus to the stop above the graphic",
        )
    }

    @Test
    fun replacingTheStateMachineWhileTheSurfaceIsFocusedKeepsTabInTheGraphic() {
        val replacement = showGraphicWithReplaceableStateMachine()
        // A tap focuses the surface without entering Rive, which a Tab onto it would.
        composeRule.onNodeWithTag(GRAPHIC_TAG).performClick()
        assertTrue(awaitCondition { surfaceFocused }, "tapping the graphic did not focus it")

        composeRule.runOnIdle { useReplacement.value = true }
        composeRule.waitForIdle()
        pressTab()

        // The focus modifier does not report again when its session is replaced, so without
        // carrying surface focus over, this Tab would skip the new graphic entirely.
        assertTrue(
            awaitRiveFocus(replacement, expected = true),
            "Tab skipped the graphic after its state machine was replaced",
        )
    }

    @Test
    fun focusModeOffKeepsTheSurfaceOutOfTheFocusOrder() {
        val machine = showGraphic(RiveFocusMode.Off)

        // Skipping the graphic leaves two stops, so the second Tab must already be past it.
        pressTab()
        pressTab()

        assertTrue(stopBelowGraphicFocused, "Tab stopped on the graphic instead of skipping it")
        assertTrue(
            machine.focusState.value == RiveFocusState.Unfocused,
            "focus entered a Rive instance with mode Off",
        )
    }

    @Test
    fun focusModeOnKeepsAGraphicWithoutFocusNodesOutOfTheFocusOrder() {
        showGraphic(RiveFocusMode.On, fixture = R.raw.empty)
        // Until the first poll answers, the surface is still a stop.
        assertTrue(
            awaitCondition { publishedHasFocusNodes?.value == false },
            "the fixture was never reported as having no focus nodes",
        )

        pressTab()
        pressTab()

        assertTrue(stopBelowGraphicFocused, "Tab stopped on a graphic with no focus nodes")
    }

    @Test
    fun tabOntoTheSurfaceEntersTheGraphicOnArrival() {
        val machine = showGraphic(RiveFocusMode.On)
        pressTab()

        // The press that reaches the surface enters the graphic too.
        pressTab()

        assertTrue(awaitRiveFocus(machine, expected = true), "arriving by Tab did not enter")
    }

    @Test
    fun shiftTabOntoTheSurfaceEntersTheGraphicAtItsLastNode() {
        val machine = showGraphic(RiveFocusMode.On)
        enterFocusTree(machine)
        leaveFocusTree(machine, press = ::pressTab)
        assertTrue(awaitCondition { stopBelowGraphicFocused }, "Tab never left the graphic")

        pressShiftTab()
        assertTrue(awaitRiveFocus(machine, expected = true), "arriving by Shift+Tab did not enter")

        // Only the last node leaves on the very next Tab.
        pressTab()
        assertTrue(
            awaitCondition { stopBelowGraphicFocused },
            "Shift+Tab entry did not land on the last node",
        )
    }

    @Test
    fun tappingTheGraphicFocusesTheSurfaceWithoutEnteringIt() {
        val machine = showGraphic(RiveFocusMode.On)

        composeRule.onNodeWithTag(GRAPHIC_TAG).performClick()

        assertTrue(awaitCondition { surfaceFocused }, "tapping the graphic did not focus it")
        assertTrue(
            machine.focusState.value == RiveFocusState.Unfocused,
            "tapping entered the focus tree",
        )

        // Focus is already on the surface, so one Tab enters the graphic.
        pressTab()

        assertTrue(awaitRiveFocus(machine, expected = true), "Tab after a tap did not enter")
    }

    /**
     * Displays one real fixture through the production composable, between two focus stops.
     */
    private fun showGraphic(
        focus: RiveFocusMode,
        @RawRes fixture: Int = R.raw.focus_nodes_list_order,
    ): StateMachine {
        composeRule.setContent {
            // Reading the worker inside composition is required; the test thread cannot touch it
            // without breaking the load.
            val worker = riveWorker
            val result = rememberTestRiveResources(
                source = RiveFileSource.RawRes.from(fixture),
                riveWorker = worker,
            )
            val resources = (result as? Result.Success)?.value
            Column {
                Box(
                    Modifier
                        .requiredSize(STOP_SIZE)
                        .onFocusChanged { stopAboveGraphicFocused = it.isFocused }
                        .focusTarget()
                )
                if (resources != null) {
                    publishedHasFocusNodes =
                        worker.reportedHasFocusNodes(resources.stateMachine.stateMachineHandle)
                    published = resources.stateMachine
                    Rive(
                        file = resources.file,
                        modifier = Modifier
                            .requiredSize(GRAPHIC_SIZE)
                            .testTag(GRAPHIC_TAG)
                            .onFocusChanged { surfaceFocused = it.hasFocus },
                        // Compose never goes idle while a graphic is playing, so the test would
                        // hang waiting for it.
                        playing = false,
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        focus = focus,
                    )
                }
                Box(
                    Modifier
                        .requiredSize(STOP_SIZE)
                        .onFocusChanged { stopBelowGraphicFocused = it.isFocused }
                        .focusTarget()
                )
            }
        }
        return awaitStateMachine()
    }

    /**
     * Displays a graphic whose state machine the test can replace, between two focus stops.
     *
     * @return The replacement state machine, shown once [useReplacement] is set.
     */
    private fun showGraphicWithReplaceableStateMachine(): StateMachine {
        composeRule.setContent {
            val worker = riveWorker
            val result = rememberTestRiveResources(
                source = RiveFileSource.RawRes.from(R.raw.focus_nodes_list_order),
                riveWorker = worker,
            )
            val resources = (result as? Result.Success)?.value
            val replacement = resources?.let {
                (rememberStateMachineResult(it.artboard) as? Result.Success)?.value
            }
            Column {
                Box(Modifier.requiredSize(STOP_SIZE).focusTarget())
                if (resources != null && replacement != null) {
                    published = replacement
                    Rive(
                        file = resources.file,
                        modifier = Modifier
                            .requiredSize(GRAPHIC_SIZE)
                            .testTag(GRAPHIC_TAG)
                            .onFocusChanged { surfaceFocused = it.hasFocus },
                        playing = false,
                        artboard = resources.artboard,
                        stateMachine = if (useReplacement.value) {
                            replacement
                        } else {
                            resources.stateMachine
                        },
                        focus = RiveFocusMode.On,
                    )
                }
                Box(Modifier.requiredSize(STOP_SIZE).focusTarget())
            }
        }
        return awaitStateMachine()
    }

    /**
     * Presses Tab until the graphic takes focus, failing if it never does.
     */
    private fun enterFocusTree(machine: StateMachine) {
        repeat(MAX_TABS_TO_ENTER) {
            pressTab()
            if (awaitRiveFocus(machine, expected = true)) return
        }
        fail("Rive never took focus within $MAX_TABS_TO_ENTER presses")
    }

    /**
     * Presses a traversal key until the graphic hands focus back, failing if it never does.
     */
    private fun leaveFocusTree(machine: StateMachine, press: () -> Unit) {
        repeat(MAX_TABS_TO_LEAVE) {
            press()
            if (awaitRiveFocus(machine, expected = false)) return
        }
        fail("Rive never released focus within $MAX_TABS_TO_LEAVE presses")
    }

    /**
     * Drives idleness until the asynchronous file load publishes its state machine.
     */
    private fun awaitStateMachine(): StateMachine {
        // waitUntil starves the load continuation, so this polls instead.
        repeat(AWAIT_ATTEMPTS) {
            published?.let { return it }
            Thread.sleep(AWAIT_STEP_MS)
        }
        fail("the state machine was never published")
    }

    /**
     * Waits for the polled focus state to reach [expected].
     */
    private fun awaitRiveFocus(machine: StateMachine, expected: Boolean): Boolean =
        awaitCondition { (machine.focusState.value is RiveFocusState.Focused) == expected }

    /**
     * Waits for [condition] to hold, since a traversal result and its hand-off land asynchronously.
     */
    private fun awaitCondition(condition: () -> Boolean): Boolean {
        repeat(AWAIT_ATTEMPTS) {
            if (condition()) return true
            composeRule.waitForIdle()
            Thread.sleep(AWAIT_STEP_MS)
        }
        return condition()
    }

    private fun pressTab() {
        composeRule.onRoot().performKeyInput { pressKey(Key.Tab) }
        composeRule.waitForIdle()
    }

    /**
     * Presses Shift+Tab.
     */
    private fun pressShiftTab() {
        composeRule.onRoot().performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Tab)
            keyUp(Key.ShiftLeft)
        }
        composeRule.waitForIdle()
    }

    private companion object {
        val STOP_SIZE = 40.dp
        val GRAPHIC_SIZE = 200.dp
        const val GRAPHIC_TAG = "graphic"
        const val AWAIT_ATTEMPTS = 60
        const val AWAIT_STEP_MS = 50L
        const val MAX_TABS_TO_ENTER = 4
        const val MAX_TABS_TO_LEAVE = 40
    }
}
