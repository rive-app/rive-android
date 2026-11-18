package app.rive

import android.view.KeyEvent
import android.view.View
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * A controller over a mocked state machine whose traversals stay in flight until completed.
 *
 * @param mode The focus mode the controller starts with.
 * @param hasFocusNodes The initial reported focus node answer.
 */
private class FocusControllerTestFixture(
    mode: RiveFocusMode = RiveFocusMode.On,
    hasFocusNodes: Boolean? = true,
) {
    val focusState = MutableStateFlow<RiveFocusState>(RiveFocusState.Unfocused)
    val hasFocusNodes = MutableStateFlow(hasFocusNodes)

    /** The direction and pending result of every move the controller started. */
    val traversals =
        mutableListOf<Pair<RiveFocusDirection, CompletableDeferred<RiveFocusMoveResult>>>()

    val stateMachine = mockk<StateMachine>().also { stateMachine ->
        every { stateMachine.focusState } returns focusState
        every { stateMachine.unsettle() } just runs
        every { stateMachine.pollFocus() } just runs
        every { stateMachine.clearFocusForLifecycleCleanup() } just runs
        coEvery { stateMachine.moveFocus(any()) } coAnswers {
            val result = CompletableDeferred<RiveFocusMoveResult>()
            traversals += firstArg<RiveFocusDirection>() to result
            result.await()
        }
        // The focused element never uses a key here, so navigation keys always move focus.
        coEvery { stateMachine.keyInput(any()) } returns false
    }

    /** The view that takes focus when navigation leaves the instance. */
    val nextView = mockk<View>().also { every { it.requestFocus(any()) } returns true }

    val host = mockk<View>().also { host ->
        every { host.focusSearch(any()) } returns nextView
        every { host.isInTouchMode } returns false
    }

    val controller = RiveCanvasFocusController(mode, stateMachine, this.hasFocusNodes)

    /**
     * Delivers the result of the latest move.
     *
     * @param hasFocus Whether Rive still holds focus after the move.
     * @param moved Whether focus moved. Defaults to [hasFocus], which fits a sequential move that
     *    lands on a node or runs off the end.
     */
    fun completeTraversal(hasFocus: Boolean, moved: Boolean = hasFocus) {
        traversals.last().second.complete(
            RiveFocusMoveResult(moved, riveFocusStateOf(hasFocus, expectsKeyboardInput = false))
        )
    }

    /** Focuses the host by forward traversal and lets Rive take its first node. */
    fun enter() {
        controller.onFocusChanged(host, gainFocus = true, direction = View.FOCUS_FORWARD)
        completeTraversal(hasFocus = true)
        focusState.value = RiveFocusState.Focused(expectsKeyboardInput = false)
    }
}

/**
 * Creates a mocked key press with no modifiers other than Shift.
 *
 * @param keyCode The Android key code.
 * @param shift Whether Shift is held.
 */
private fun keyPress(keyCode: Int, shift: Boolean = false): KeyEvent =
    mockk<KeyEvent>().also { event ->
        every { event.keyCode } returns keyCode
        every { event.action } returns KeyEvent.ACTION_DOWN
        every { event.repeatCount } returns 0
        every { event.isShiftPressed } returns shift
        every { event.isCtrlPressed } returns false
        every { event.isAltPressed } returns false
        every { event.isMetaPressed } returns false
        every { event.hasNoModifiers() } returns !shift
        every { event.hasModifiers(any()) } answers {
            shift && firstArg<Int>() == KeyEvent.META_SHIFT_ON
        }
    }

/**
 * Creates a mocked `Tab` press.
 *
 * @param shift Whether Shift is held.
 */
private fun tabPress(shift: Boolean = false): KeyEvent = keyPress(KeyEvent.KEYCODE_TAB, shift)

@OptIn(ExperimentalCoroutinesApi::class)
class RiveCanvasFocusControllerUnitTest : FunSpec({
    val fixtures = mutableListOf<FocusControllerTestFixture>()

    /** Creates a fixture that is closed after the test, so its coroutines don't outlive it. */
    fun fixture(
        mode: RiveFocusMode = RiveFocusMode.On,
        hasFocusNodes: Boolean? = true,
    ): FocusControllerTestFixture =
        FocusControllerTestFixture(mode, hasFocusNodes).also(fixtures::add)

    beforeTest { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterTest {
        fixtures.forEach { it.controller.close() }
        fixtures.clear()
        Dispatchers.resetMain()
    }

    context("close") {
        test("Clears focus Rive holds") {
            val fixture = fixture()
            fixture.enter()

            fixture.controller.close()

            verify { fixture.stateMachine.clearFocusForLifecycleCleanup() }
        }

        test("Resolves focusability to None") {
            val fixture = fixture()

            fixture.controller.close()

            fixture.controller.focusability.value shouldBe RiveFocusability.None
        }

        test("A traversal result after close does not move Android focus") {
            val fixture = fixture()
            fixture.enter()
            fixture.controller.onKeyEvent(tabPress())

            fixture.controller.close()
            fixture.completeTraversal(hasFocus = false)

            verify(exactly = 0) { fixture.nextView.requestFocus(any()) }
        }
    }

    context("mode") {
        test("Each mode resolves to its focusability as soon as it is set") {
            val fixture = fixture(mode = RiveFocusMode.On)

            fixture.controller.focusability.value shouldBe RiveFocusability.KeyboardAndTouch
            fixture.controller.mode = RiveFocusMode.Automatic
            fixture.controller.focusability.value shouldBe RiveFocusability.Keyboard
            fixture.controller.mode = RiveFocusMode.Off
            fixture.controller.focusability.value shouldBe RiveFocusability.None
        }

        test("Turning focus on unsettles the state machine") {
            val fixture = fixture(mode = RiveFocusMode.Off)

            fixture.controller.mode = RiveFocusMode.On

            verify(exactly = 1) { fixture.stateMachine.unsettle() }
        }

        test("Setting the current mode does nothing") {
            val fixture = fixture(mode = RiveFocusMode.On)

            fixture.controller.mode = RiveFocusMode.On

            verify(exactly = 0) { fixture.stateMachine.unsettle() }
        }

        test("Turning focus on with a closed state machine throws before changing anything") {
            val fixture = fixture(mode = RiveFocusMode.Off)
            every { fixture.stateMachine.unsettle() } throws
                RiveResourceClosedException("StateMachine is closed")

            shouldThrowUnit<RiveResourceClosedException> {
                fixture.controller.mode = RiveFocusMode.On
            }

            fixture.controller.mode shouldBe RiveFocusMode.Off
            fixture.controller.focusability.value shouldBe RiveFocusability.None
        }

        test("Turning focus off while Rive holds a node clears it") {
            val fixture = fixture()
            fixture.enter()

            fixture.controller.mode = RiveFocusMode.Off

            verify { fixture.stateMachine.clearFocusForLifecycleCleanup() }
            fixture.controller.onKeyEvent(tabPress()) shouldBe false
        }

        test("A host focused before focus turned on can enter with the next Tab") {
            val fixture = fixture(mode = RiveFocusMode.Off)
            fixture.controller.onFocusChanged(fixture.host, gainFocus = true)

            fixture.controller.mode = RiveFocusMode.On

            fixture.controller.onKeyEvent(tabPress()) shouldBe true
            fixture.traversals.map { it.first } shouldContainExactly listOf(RiveFocusDirection.Next)
        }
    }

    context("focus nodes") {
        test("No focusable content resolves to None and releases Rive focus") {
            val fixture = fixture()
            fixture.enter()

            fixture.hasFocusNodes.value = false

            fixture.controller.focusability.value shouldBe RiveFocusability.None
            verify { fixture.stateMachine.clearFocusForLifecycleCleanup() }
        }

        test("Focusable content appearing later makes the host focusable") {
            val fixture = fixture(hasFocusNodes = false)

            fixture.hasFocusNodes.value = true

            fixture.controller.focusability.value shouldBe RiveFocusability.KeyboardAndTouch
        }
    }

    context("onAdvanced") {
        test("Polls focus while focus is on") {
            val fixture = fixture(mode = RiveFocusMode.Automatic)

            fixture.controller.onAdvanced()

            verify(exactly = 1) { fixture.stateMachine.pollFocus() }
        }

        test("Polls focus even without focusable content, so content added later is seen") {
            val fixture = fixture(hasFocusNodes = false)

            fixture.controller.onAdvanced()

            verify(exactly = 1) { fixture.stateMachine.pollFocus() }
        }

        test("Does not poll while focus is off") {
            val fixture = fixture(mode = RiveFocusMode.Off)

            fixture.controller.onAdvanced()

            verify(exactly = 0) { fixture.stateMachine.pollFocus() }
        }
    }

    context("onFocusChanged") {
        test("Tab, Right, and Down enter with Next; Shift+Tab, Left, and Up with Previous") {
            listOf(
                View.FOCUS_FORWARD to RiveFocusDirection.Next,
                View.FOCUS_RIGHT to RiveFocusDirection.Next,
                View.FOCUS_DOWN to RiveFocusDirection.Next,
                View.FOCUS_BACKWARD to RiveFocusDirection.Previous,
                View.FOCUS_LEFT to RiveFocusDirection.Previous,
                View.FOCUS_UP to RiveFocusDirection.Previous,
            ).forAll { (androidDirection, expected) ->
                val fixture = fixture()

                fixture.controller.onFocusChanged(fixture.host, true, androidDirection)

                fixture.traversals.map { it.first } shouldContainExactly listOf(expected)
            }
        }

        test("A host that already holds focus enters with Tab once it reports that focus") {
            val fixture = fixture()
            // A new controller can't know the host already holds focus.
            fixture.controller.onKeyEvent(tabPress()) shouldBe false

            fixture.controller.onFocusChanged(fixture.host, true)

            fixture.traversals.shouldBeEmpty()
            fixture.controller.onKeyEvent(tabPress()) shouldBe true
        }

        test("Arrow-direction arrivals in touch mode, like a tap's FOCUS_DOWN, don't enter") {
            val spatialDirections =
                listOf(View.FOCUS_LEFT, View.FOCUS_RIGHT, View.FOCUS_UP, View.FOCUS_DOWN)
            spatialDirections.forAll { androidDirection ->
                val fixture = fixture()
                every { fixture.host.isInTouchMode } returns true

                fixture.controller.onFocusChanged(fixture.host, true, androidDirection)

                coVerify(exactly = 0) { fixture.stateMachine.moveFocus(any()) }
            }
        }

        test("Losing focus clears Rive focus") {
            val fixture = fixture()
            fixture.enter()

            fixture.controller.onFocusChanged(fixture.host, false)

            verify { fixture.stateMachine.clearFocusForLifecycleCleanup() }
        }
    }

    context("onKeyEvent") {
        test("A traversal keypress is consumed and starts a traversal in its direction") {
            val fixture = fixture()
            fixture.enter()

            fixture.controller.onKeyEvent(tabPress()) shouldBe true
            fixture.completeTraversal(hasFocus = true)
            fixture.controller.onKeyEvent(tabPress(shift = true)) shouldBe true

            fixture.traversals.drop(1).map { it.first } shouldContainExactly listOf(
                RiveFocusDirection.Next,
                RiveFocusDirection.Previous,
            )
        }

        test("Leaving the instance moves Android focus to the next view in that direction") {
            val fixture = fixture()
            fixture.enter()

            fixture.controller.onKeyEvent(tabPress(shift = true))
            fixture.completeTraversal(hasFocus = false)

            verify { fixture.nextView.requestFocus(View.FOCUS_BACKWARD) }
        }

        test("Hands off spatially through focusSearch") {
            val fixture = fixture()
            fixture.enter()

            fixture.controller.onKeyEvent(keyPress(KeyEvent.KEYCODE_DPAD_DOWN))
            fixture.completeTraversal(hasFocus = true, moved = false)

            verify { fixture.nextView.requestFocus(View.FOCUS_DOWN) }
        }

        test("A search that wraps back to the host lets the next Tab enter again") {
            val fixture = fixture()
            every { fixture.host.focusSearch(any()) } returns fixture.host
            fixture.enter()

            fixture.controller.onKeyEvent(tabPress())
            fixture.completeTraversal(hasFocus = false)
            fixture.focusState.value = RiveFocusState.Unfocused

            fixture.controller.onKeyEvent(tabPress()) shouldBe true
            fixture.traversals.size shouldBe 3
        }
    }
})
