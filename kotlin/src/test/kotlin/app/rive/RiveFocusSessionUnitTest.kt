package app.rive

import android.view.KeyEvent
import app.rive.RiveFocusSession.FocusPhase
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * A `Tab` key event.
 *
 * @param shift Whether Shift is held, which makes it `Shift+Tab`.
 * @param isPressed Whether the key went down rather than up.
 */
private fun tab(shift: Boolean = false, isPressed: Boolean = true) = RiveKeyPress(
    keyCode = KeyEvent.KEYCODE_TAB,
    key = 258,
    modifiers = if (shift) 1 else 0,
    isPressed = isPressed,
    isRepeat = false,
    navigation = if (shift) RiveFocusDirection.Previous else RiveFocusDirection.Next,
    isChord = false,
)

/**
 * An arrow key event.
 *
 * @param direction The spatial direction of the arrow.
 * @param isPressed Whether the key went down rather than up.
 */
private fun arrow(direction: RiveFocusDirection, isPressed: Boolean = true): RiveKeyPress {
    val (keyCode, key) = when (direction) {
        RiveFocusDirection.Left -> KeyEvent.KEYCODE_DPAD_LEFT to 263
        RiveFocusDirection.Right -> KeyEvent.KEYCODE_DPAD_RIGHT to 262
        RiveFocusDirection.Up -> KeyEvent.KEYCODE_DPAD_UP to 265
        RiveFocusDirection.Down -> KeyEvent.KEYCODE_DPAD_DOWN to 264
        else -> error("$direction is not spatial")
    }
    return RiveKeyPress(
        keyCode = keyCode,
        key = key,
        modifiers = 0,
        isPressed = isPressed,
        isRepeat = false,
        navigation = direction,
        isChord = false,
    )
}

/**
 * A key event for the letter A.
 *
 * @param ctrl Whether Ctrl is held, which makes it a shortcut.
 * @param isPressed Whether the key went down rather than up.
 */
private fun letter(ctrl: Boolean = false, isPressed: Boolean = true) = RiveKeyPress(
    keyCode = KeyEvent.KEYCODE_A,
    key = 65,
    modifiers = if (ctrl) 2 else 0,
    isPressed = isPressed,
    isRepeat = false,
    navigation = null,
    isChord = ctrl,
)

/**
 * A session whose moves stay in flight until the test completes them.
 *
 * Keys the session sends to the focused element are answered at once with [keysHandled], unless
 * [holdKeyAnswers] keeps them waiting for [answerKey]. Work runs in the test's background scope,
 * which [runTest] cancels when the test ends.
 *
 * @param testScope The test driving the session's coroutines and virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class FocusSessionTestFixture(private val testScope: TestScope) {
    /** The direction and pending result of every move the session started. */
    val traversals =
        mutableListOf<Pair<RiveFocusDirection, CompletableDeferred<RiveFocusMoveResult>>>()

    /** Every key the session delivered to the focused element. */
    val keys = mutableListOf<RiveKeyPress>()

    /** What the focused element reports for each key it receives. */
    var keysHandled = false

    /** Whether offered keys wait for [answerKey] instead of answering at once. */
    var holdKeyAnswers = false

    /** Answers the test is holding back, oldest first. */
    private val pendingKeyAnswers = mutableListOf<CompletableDeferred<Boolean>>()

    /** Every key delivered and every move started, in the order the session submitted them. */
    val submissions = mutableListOf<Any>()

    /** The direction of every hand-off to the host. */
    val handOffs = mutableListOf<RiveFocusDirection>()

    /** Host focus changes that happen inside a hand-off, before it returns. */
    var onHandOff: (direction: RiveFocusDirection) -> Unit = {}

    val clearFocus = mockk<() -> Unit>(relaxed = true)
    val requestFrame = mockk<() -> Unit>(relaxed = true)

    val session = RiveFocusSession(
        scope = testScope.backgroundScope,
        move = { direction ->
            submissions += direction
            CompletableDeferred<RiveFocusMoveResult>().also { traversals += direction to it }
                .await()
        },
        sendKey = { press ->
            keys += press
            submissions += press
            if (holdKeyAnswers) {
                CompletableDeferred<Boolean>().also { pendingKeyAnswers += it }.await()
            } else {
                keysHandled
            }
        },
        handOff = { direction ->
            handOffs += direction
            onHandOff(direction)
        },
        clearFocus = clearFocus,
        requestFrame = requestFrame,
    )

    /** Starts the session with the surface focused and Rive holding focus. */
    fun focusedWithRiveFocus() {
        session.onSurfaceFocusChanged(focused = true)
        session.onRiveFocusChanged(RiveFocusState.Focused(expectsKeyboardInput = false))
    }

    /**
     * Answers the oldest key the test is holding back.
     *
     * @param handled Whether the element handled the key.
     */
    fun answerKey(handled: Boolean) {
        pendingKeyAnswers.removeAt(0).complete(handled)
        testScope.runCurrent()
    }

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
        testScope.runCurrent()
    }
}

class RiveFocusSessionUnitTest : FunSpec({
    context("dispose") {
        test("Disposing mid-traversal abandons it and clears the focus it may take") {
            runTest {
                // Turning focus off removes the handler, so nothing else would clear a ring drawn
                // by the traversal already queued. The scope outlives the session, so without the
                // cancel the late result would still hand off.
                val fixture = FocusSessionTestFixture(this)
                fixture.session.onSurfaceFocusChanged(focused = true)
                fixture.session.onKeyEvent(tab())

                fixture.session.dispose()

                fixture.session.traversing shouldBe false
                fixture.completeTraversal(hasFocus = false)
                verify(exactly = 1) { fixture.clearFocus() }
                fixture.handOffs.shouldBeEmpty()
            }
        }

        test("Disposing while Rive holds focus clears it") {
            runTest {
                // Without surface focus, so the clear comes from Rive's focus alone.
                val fixture = FocusSessionTestFixture(this)
                fixture.session.onRiveFocusChanged(
                    RiveFocusState.Focused(expectsKeyboardInput = false)
                )

                fixture.session.dispose()

                verify(exactly = 1) { fixture.clearFocus() }
                fixture.session.phase shouldNotBe FocusPhase.Focused
            }
        }

        test("Disposing while the surface holds focus clears focus the poll has not reported yet") {
            runTest {
                val fixture = FocusSessionTestFixture(this)
                fixture.session.onSurfaceFocusChanged(focused = true)

                fixture.session.dispose()

                verify(exactly = 1) { fixture.clearFocus() }
            }
        }

        test("Disposing a session that never held any focus leaves the instance alone") {
            runTest {
                val fixture = FocusSessionTestFixture(this)

                fixture.session.dispose()

                verify(exactly = 0) { fixture.clearFocus() }
            }
        }
    }

    context("onSurfaceFocusChanged") {
        test("Gaining focus by traversal enters the tree at once, in that direction") {
            runTest {
                listOf(RiveFocusDirection.Next, RiveFocusDirection.Previous).forAll { direction ->
                    val fixture = FocusSessionTestFixture(this)

                    fixture.session.onSurfaceFocusChanged(
                        focused = true,
                        entryDirection = direction
                    )
                    fixture.completeTraversal(hasFocus = true)

                    fixture.traversals.map { it.first } shouldContainExactly listOf(direction)
                    fixture.session.phase shouldBe FocusPhase.Focused
                    fixture.handOffs.shouldBeEmpty()
                }
            }
        }

        test("Gaining focus without a direction doesn't enter the tree") {
            runTest {
                // A tap focuses the surface, and entering here would move focus off what it tapped.
                val fixture = FocusSessionTestFixture(this)

                fixture.session.onSurfaceFocusChanged(focused = true)

                fixture.traversals.shouldBeEmpty()
            }
        }

        test("Gaining focus while Rive already holds a node leaves it alone") {
            runTest {
                val fixture = FocusSessionTestFixture(this)
                fixture.session.onRiveFocusChanged(
                    RiveFocusState.Focused(expectsKeyboardInput = false)
                )

                fixture.session.onSurfaceFocusChanged(
                    focused = true,
                    entryDirection = RiveFocusDirection.Next
                )

                fixture.traversals.shouldBeEmpty()
            }
        }

        test("An entry that finds nothing to focus stays on the surface") {
            runTest {
                // Handing off could wrap straight back to a surface that is the only focus stop.
                val fixture = FocusSessionTestFixture(this)

                fixture.session.onSurfaceFocusChanged(
                    focused = true,
                    entryDirection = RiveFocusDirection.Next
                )
                fixture.completeTraversal(hasFocus = false)

                fixture.handOffs.shouldBeEmpty()
                fixture.session.phase shouldBe FocusPhase.Ready
                fixture.session.onKeyEvent(tab()) shouldBe true
                fixture.traversals shouldHaveSize 2
            }
        }

        test("Losing surface focus stops the session handling keys") {
            runTest {
                val fixture = FocusSessionTestFixture(this)
                fixture.focusedWithRiveFocus()

                fixture.session.onSurfaceFocusChanged(focused = false)

                fixture.session.onKeyEvent(tab()) shouldBe false
                fixture.traversals.shouldBeEmpty()
            }
        }

        test("Losing surface focus forgets held keys, so their releases pass through") {
            runTest {
                val fixture = FocusSessionTestFixture(this)
                fixture.session.onSurfaceFocusChanged(focused = true)
                fixture.session.onRiveFocusChanged(
                    RiveFocusState.Focused(expectsKeyboardInput = true)
                )
                fixture.session.onKeyEvent(letter())

                fixture.session.onSurfaceFocusChanged(focused = false)

                fixture.session.onKeyEvent(letter(isPressed = false)) shouldBe false
                fixture.keys shouldHaveSize 1
            }
        }

        test("Losing surface focus drops focus the instance still holds") {
            runTest {
                // Otherwise the instance keeps drawing its ring beside whatever the host focused
                // next.
                val fixture = FocusSessionTestFixture(this)
                fixture.focusedWithRiveFocus()

                fixture.session.onSurfaceFocusChanged(focused = false)

                verify(exactly = 1) { fixture.clearFocus() }
                verify(exactly = 1) { fixture.requestFrame() }
                fixture.session.phase shouldNotBe FocusPhase.Focused
            }
        }

        test("Losing surface focus clears focus the poll has not reported yet") {
            runTest {
                // A tap focuses the surface and a node together, and the poll reports the node
                // later.
                val fixture = FocusSessionTestFixture(this)
                fixture.session.onSurfaceFocusChanged(focused = true)

                fixture.session.onSurfaceFocusChanged(focused = false)

                verify(exactly = 1) { fixture.clearFocus() }
            }
        }

        test("Repeating a surface focus loss clears focus only once") {
            runTest {
                val fixture = FocusSessionTestFixture(this)
                fixture.focusedWithRiveFocus()

                fixture.session.onSurfaceFocusChanged(focused = false)
                fixture.session.onSurfaceFocusChanged(focused = false)

                verify(exactly = 1) { fixture.clearFocus() }
            }
        }

        test("Losing surface focus mid-traversal abandons it and clears the focus it may take") {
            runTest {
                // Entering the tree from nothing: the traversal may take focus after the surface
                // lost it, which would leave a ring drawn beside whatever the host focused next.
                val fixture = FocusSessionTestFixture(this)
                fixture.session.onSurfaceFocusChanged(focused = true)
                fixture.session.onKeyEvent(tab())

                fixture.session.onSurfaceFocusChanged(focused = false)

                fixture.session.traversing shouldBe false
                fixture.completeTraversal(hasFocus = false)
                verify(exactly = 1) { fixture.clearFocus() }
                // A late result applied anyway would release the session.
                fixture.session.phase shouldBe FocusPhase.Ready
            }
        }
    }

    context("onRiveFocusChanged") {
        test("Rive clearing focus on its own lets the next Tab enter again") {
            runTest {
                // For example, the focused element hid, or a press landed outside a text input.
                val fixture = FocusSessionTestFixture(this)
                fixture.focusedWithRiveFocus()
                fixture.session.onRiveFocusChanged(RiveFocusState.Unfocused)

                fixture.session.onKeyEvent(tab()) shouldBe true

                fixture.traversals.map { it.first } shouldContainExactly
                    listOf(RiveFocusDirection.Next)
            }
        }
    }

    context("onKeyEvent") {
        context("Tab") {
            test("A Tab is consumed before Rive answers") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(tab()) shouldBe true

                    fixture.traversals.map { it.first } shouldContainExactly
                        listOf(RiveFocusDirection.Next)
                    fixture.session.traversing shouldBe true
                }
            }

            test("A Tab enters the tree when Rive holds nothing and nothing was released") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.session.onSurfaceFocusChanged(focused = true)

                    fixture.session.onKeyEvent(tab(shift = true)) shouldBe true

                    // No element holds focus, so the key goes straight to the move.
                    fixture.keys.shouldBeEmpty()
                    fixture.traversals.map { it.first } shouldContainExactly
                        listOf(RiveFocusDirection.Previous)
                }
            }

            test("Running out of the tree hands focus to the host in the key's direction") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(tab(shift = true))
                    fixture.completeTraversal(hasFocus = false)

                    fixture.handOffs shouldContainExactly listOf(RiveFocusDirection.Previous)
                    fixture.session.phase shouldBe FocusPhase.Ready
                }
            }

            test("A stop edge keeps focus and doesn't hand off") {
                runTest {
                    // A stop keeps focus while reporting no movement. Handing off here would walk
                    // focus out of a trap the instance set.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(tab())
                    fixture.completeTraversal(hasFocus = true, moved = false)

                    fixture.handOffs.shouldBeEmpty()
                    fixture.session.phase shouldBe FocusPhase.Focused
                    fixture.session.onKeyEvent(tab()) shouldBe true
                    fixture.traversals shouldHaveSize 2
                }
            }

            test("When the surface keeps focus after running out, the next Tab re-enters") {
                runTest {
                    // The host found nowhere to move focus, so the surface kept it.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(tab())
                    fixture.completeTraversal(hasFocus = false)

                    fixture.session.phase shouldBe FocusPhase.Ready
                    fixture.session.onKeyEvent(tab()) shouldBe true
                    fixture.traversals shouldHaveSize 2
                }
            }

            test("A hand-off that wraps back to the surface starts a new entry") {
                runTest {
                    // With nothing else to focus, Compose wraps around by clearing focus and
                    // taking it again, so the surface loses and regains focus inside the hand-off.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.onHandOff = { direction ->
                        fixture.session.onSurfaceFocusChanged(focused = false)
                        fixture.session.onSurfaceFocusChanged(
                            focused = true,
                            entryDirection = direction,
                        )
                    }
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(tab())
                    fixture.completeTraversal(hasFocus = false)

                    fixture.traversals.map { it.first } shouldContainExactly
                        listOf(RiveFocusDirection.Next, RiveFocusDirection.Next)
                    fixture.session.traversing shouldBe true
                    // Held for the entry in flight, so nothing goes out yet.
                    fixture.session.onKeyEvent(letter()) shouldBe true
                    fixture.keys shouldContainExactly listOf(tab())
                }
            }

            test("A traversal asks for a frame even when it releases to the host") {
                runTest {
                    // A paused instance draws once, so the ring would survive the focus that
                    // justified it.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.session.onSurfaceFocusChanged(focused = true)

                    fixture.session.onKeyEvent(tab())
                    fixture.completeTraversal(hasFocus = false)

                    verify(exactly = 1) { fixture.requestFrame() }
                }
            }

            test("A move that fails hands focus off to the host") {
                runTest {
                    // The command server answers a handle it does not know with an error.
                    // Swallowing every key on an instance that cannot move focus would trap
                    // keyboard focus on the surface.
                    val handOffs = mutableListOf<RiveFocusDirection>()

                    val session = RiveFocusSession(
                        scope = backgroundScope,
                        move = { throw RiveStateMachineException("State machine not found") },
                        sendKey = { false },
                        handOff = { direction -> handOffs += direction },
                        clearFocus = {},
                        requestFrame = {},
                    )
                    session.onSurfaceFocusChanged(focused = true)

                    session.onKeyEvent(tab()) shouldBe true

                    session.traversing shouldBe false
                    session.phase shouldBe FocusPhase.Ready
                    handOffs shouldContainExactly listOf(RiveFocusDirection.Next)
                }
            }

            @OptIn(ExperimentalCoroutinesApi::class)
            test("A timed-out traversal allows the next keypress to submit a new traversal") {
                runTest {
                    // Polling stops below RESUMED, so an answer can stall while the window still
                    // gets keys.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.session.onKeyEvent(tab())

                    // A slow answer within the timeout still counts, so keys stay claimed until
                    // then.
                    advanceTimeBy(900.milliseconds)
                    fixture.session.traversing shouldBe true
                    advanceTimeBy(200.milliseconds)
                    runCurrent()

                    fixture.session.traversing shouldBe false
                    fixture.handOffs.shouldBeEmpty()
                    fixture.session.onKeyEvent(tab()) shouldBe true
                    fixture.traversals shouldHaveSize 2
                }
            }
        }

        context("Arrow keys") {
            test("Pass through while Rive holds nothing") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.session.onSurfaceFocusChanged(focused = true)

                    fixture.session.onKeyEvent(arrow(RiveFocusDirection.Right)) shouldBe false

                    fixture.keys.shouldBeEmpty()
                    fixture.traversals.shouldBeEmpty()
                }
            }

            test("Are offered to the element, then move when unhandled") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(arrow(RiveFocusDirection.Right)) shouldBe true

                    fixture.keys shouldContainExactly listOf(arrow(RiveFocusDirection.Right))
                    fixture.traversals.map { it.first } shouldContainExactly
                        listOf(RiveFocusDirection.Right)
                }
            }

            test("An arrow the element handles, such as a caret move, doesn't move focus") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.keysHandled = true
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(arrow(RiveFocusDirection.Left)) shouldBe true

                    fixture.traversals.shouldBeEmpty()
                }
            }

            test("An arrow that can't move hands off in its direction") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(arrow(RiveFocusDirection.Down))
                    fixture.completeTraversal(hasFocus = true, moved = false)

                    fixture.handOffs shouldContainExactly listOf(RiveFocusDirection.Down)
                }
            }

            test("An arrow that moves keeps focus inside the instance") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(arrow(RiveFocusDirection.Down))
                    fixture.completeTraversal(hasFocus = true, moved = true)

                    fixture.handOffs.shouldBeEmpty()
                    fixture.session.phase shouldBe FocusPhase.Focused
                }
            }

            test("When the surface keeps focus after a failed arrow, the next arrow tries again") {
                runTest {
                    // The host found nowhere to move focus, so Rive keeps its node.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(arrow(RiveFocusDirection.Up))
                    fixture.completeTraversal(hasFocus = true, moved = false)

                    fixture.session.phase shouldBe FocusPhase.Focused
                    fixture.session.onKeyEvent(arrow(RiveFocusDirection.Up)) shouldBe true
                    fixture.traversals shouldHaveSize 2
                }
            }
        }

        context("Other keys") {
            test("Are delivered and consumed when the element expects keyboard input") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.session.onSurfaceFocusChanged(focused = true)
                    fixture.session.onRiveFocusChanged(
                        RiveFocusState.Focused(expectsKeyboardInput = true)
                    )

                    fixture.session.onKeyEvent(letter()) shouldBe true

                    fixture.keys shouldContainExactly listOf(letter())
                }
            }

            test("Are delivered but not consumed when the element doesn't expect keyboard input") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(letter()) shouldBe false

                    fixture.keys shouldContainExactly listOf(letter())
                }
            }

            test("Chords are delivered but never consumed") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.session.onSurfaceFocusChanged(focused = true)
                    fixture.session.onRiveFocusChanged(
                        RiveFocusState.Focused(expectsKeyboardInput = true)
                    )

                    fixture.session.onKeyEvent(letter(ctrl = true)) shouldBe false

                    fixture.keys shouldContainExactly listOf(letter(ctrl = true))
                }
            }

            test("Ask for a frame, so a paused instance draws what the key changed") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(letter())

                    verify(exactly = 1) { fixture.requestFrame() }
                }
            }
        }

        context("Releases") {
            test("Follow their press: delivered and consumed the same way") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.session.onSurfaceFocusChanged(focused = true)
                    fixture.session.onRiveFocusChanged(
                        RiveFocusState.Focused(expectsKeyboardInput = true)
                    )
                    fixture.session.onKeyEvent(letter())

                    fixture.session.onKeyEvent(letter(isPressed = false)) shouldBe true

                    fixture.keys.map { it.isPressed } shouldContainExactly listOf(true, false)
                }
            }

            test("A release whose press wasn't consumed isn't consumed either") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.session.onKeyEvent(letter())

                    fixture.session.onKeyEvent(letter(isPressed = false)) shouldBe false

                    fixture.keys.map { it.isPressed } shouldContainExactly listOf(true, false)
                }
            }

            test("A release whose press arrived before the host had focus is ignored") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.session.onKeyEvent(letter())
                    fixture.focusedWithRiveFocus()

                    fixture.session.onKeyEvent(letter(isPressed = false)) shouldBe false

                    fixture.keys.shouldBeEmpty()
                }
            }

            test("The release of a Tab that entered from nothing is consumed but not delivered") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.session.onSurfaceFocusChanged(focused = true)
                    fixture.session.onKeyEvent(tab())
                    // Lands first, so a release wrongly marked as delivered would be sent at once.
                    fixture.completeTraversal(hasFocus = true)

                    fixture.session.onKeyEvent(tab(isPressed = false)) shouldBe true

                    fixture.keys.shouldBeEmpty()
                }
            }

            test("A release follows a repeat that reached the element after entering") {
                runTest {
                    // The first Tab only moved focus in, but its repeat reached the element.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.session.onSurfaceFocusChanged(focused = true)
                    fixture.session.onKeyEvent(tab())
                    fixture.session.onKeyEvent(tab().copy(isRepeat = true))
                    fixture.completeTraversal(hasFocus = true)

                    fixture.session.onKeyEvent(tab(isPressed = false)) shouldBe true

                    fixture.keys.map { it.isPressed } shouldContainExactly listOf(true, false)
                }
            }
        }

        context("Keys pressed while a navigation is in flight") {
            test("Repeats are consumed and reach the element the move lands on, without moving") {
                runTest {
                    // A held key repeats faster than the command server answers. The repeats
                    // reach the element focus lands on instead of each starting a move from stale
                    // state.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.session.onKeyEvent(arrow(RiveFocusDirection.Right))

                    repeat(3) {
                        fixture.session.onKeyEvent(
                            arrow(RiveFocusDirection.Right).copy(isRepeat = true)
                        ) shouldBe true
                    }
                    // Only the offered press has gone out so far.
                    fixture.keys shouldHaveSize 1
                    fixture.completeTraversal(hasFocus = true)

                    fixture.keys shouldHaveSize 4
                    fixture.traversals shouldHaveSize 1
                }
            }

            test("Reach the element focus moves to, even while the offered key awaits its answer") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.holdKeyAnswers = true
                    fixture.session.onKeyEvent(tab())

                    fixture.session.onKeyEvent(letter()) shouldBe true
                    fixture.answerKey(handled = false)
                    fixture.completeTraversal(hasFocus = true)

                    fixture.submissions shouldContainExactly
                        listOf(tab(), RiveFocusDirection.Next, letter())
                }
            }

            test("Releases follow their press to the element focus moves to") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.session.onKeyEvent(tab())
                    fixture.session.onKeyEvent(letter())
                    fixture.session.onKeyEvent(letter(isPressed = false))

                    fixture.completeTraversal(hasFocus = true)

                    fixture.keys shouldContainExactly
                        listOf(tab(), letter(), letter(isPressed = false))
                }
            }

            test("Are dropped, with their releases, when the navigation leaves the instance") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.session.onKeyEvent(tab())
                    fixture.session.onKeyEvent(letter())

                    fixture.completeTraversal(hasFocus = false)
                    fixture.session.onKeyEvent(letter(isPressed = false)) shouldBe true

                    fixture.keys shouldContainExactly listOf(tab())
                }
            }

            test("Are consumed while entering, then reach the element entered") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.session.onSurfaceFocusChanged(
                        focused = true,
                        entryDirection = RiveFocusDirection.Next,
                    )

                    fixture.session.onKeyEvent(letter()) shouldBe true
                    fixture.keys.shouldBeEmpty()
                    fixture.completeTraversal(hasFocus = true)

                    fixture.keys shouldContainExactly listOf(letter())
                    fixture.traversals shouldHaveSize 1
                }
            }

            test("A new navigation key press navigates once the navigation in flight lands") {
                runTest {
                    // Each press moves focus, even one that waited for the move before it. Only
                    // repeats wait as plain keys.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.session.onKeyEvent(tab())
                    fixture.session.onKeyEvent(tab(isPressed = false))

                    fixture.session.onKeyEvent(tab()) shouldBe true
                    fixture.completeTraversal(hasFocus = true)

                    fixture.traversals shouldHaveSize 2
                }
            }

            test("Keys after a new navigation key press wait for its navigation too") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.session.onKeyEvent(tab())
                    fixture.session.onKeyEvent(tab(isPressed = false))
                    fixture.session.onKeyEvent(tab())
                    fixture.session.onKeyEvent(letter())

                    fixture.completeTraversal(hasFocus = true)
                    fixture.completeTraversal(hasFocus = true)

                    fixture.submissions shouldContainExactly listOf(
                        tab(),
                        RiveFocusDirection.Next,
                        tab(isPressed = false),
                        tab(),
                        RiveFocusDirection.Next,
                        letter(),
                    )
                }
            }

            test("The release of a Tab that only entered the instance again isn't delivered") {
                runTest {
                    // Both Tabs go down and up before the first move lands. The first leaves the
                    // instance from its last node, and the surface keeps focus with nowhere to hand
                    // off. The second then only enters the instance again.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.session.onKeyEvent(tab())
                    fixture.session.onKeyEvent(tab(isPressed = false))
                    fixture.session.onKeyEvent(tab())
                    fixture.session.onKeyEvent(tab(isPressed = false))

                    fixture.completeTraversal(hasFocus = false)
                    fixture.completeTraversal(hasFocus = true)

                    fixture.session.phase shouldBe FocusPhase.Focused
                    fixture.keys shouldContainExactly listOf(tab())
                }
            }

            test("A release isn't delivered after an entry when its press reached no element") {
                runTest {
                    // The letter's press is dropped as the first move leaves the instance, but its
                    // release waits behind the next Tab, which enters the instance again.
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.session.onKeyEvent(tab())
                    fixture.session.onKeyEvent(tab(isPressed = false))
                    fixture.session.onKeyEvent(letter())
                    fixture.session.onKeyEvent(tab())
                    fixture.session.onKeyEvent(letter(isPressed = false))

                    fixture.completeTraversal(hasFocus = false)
                    fixture.completeTraversal(hasFocus = true)

                    fixture.session.phase shouldBe FocusPhase.Focused
                    fixture.keys shouldContainExactly listOf(tab())
                }
            }

            test("Leave chords unconsumed") {
                runTest {
                    val fixture = FocusSessionTestFixture(this)
                    fixture.focusedWithRiveFocus()
                    fixture.session.onKeyEvent(tab())

                    fixture.session.onKeyEvent(letter(ctrl = true)) shouldBe false
                }
            }
        }
    }
})
