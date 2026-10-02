package app.rive

import app.rive.RiveFocusSession.FocusPhase
import io.kotest.core.spec.style.FunSpec
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
 * A session whose traversals stay in flight until the test completes them.
 *
 * Traversals run in the test's background scope, which [runTest] cancels when the test ends.
 *
 * @param testScope The test driving the session's coroutines and virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class FocusSessionTestFixture(private val testScope: TestScope) {
    /** The direction and pending result of every traversal the session started. */
    val traversals = mutableListOf<Pair<RiveFocusDirection, CompletableDeferred<RiveFocusState>>>()

    /** The direction of every hand-off to the host. */
    val handOffs = mutableListOf<RiveFocusDirection>()

    /** What the host reports when asked to move focus past the surface. */
    var hostCanMoveFocus = true

    val clearFocus = mockk<() -> Unit>(relaxed = true)
    val requestFrame = mockk<() -> Unit>(relaxed = true)

    val session = RiveFocusSession(
        scope = testScope.backgroundScope,
        traverse = { direction ->
            CompletableDeferred<RiveFocusState>().also { traversals += direction to it }.await()
        },
        handOff = { direction ->
            handOffs += direction
            hostCanMoveFocus
        },
        clearFocus = clearFocus,
        requestFrame = requestFrame,
    )

    /** Starts the session with the surface focused and Rive holding focus. */
    fun focusedWithRiveFocus() {
        session.onSurfaceFocusChanged(focused = true)
        session.onRiveFocusChanged(hasFocus = true)
    }

    /**
     * Delivers the result of the latest traversal.
     *
     * @param hasFocus Whether Rive still holds focus after the traversal.
     */
    fun completeTraversal(hasFocus: Boolean) {
        traversals.last().second.complete(riveFocusStateOf(hasFocus, expectsKeyboardInput = false))
        testScope.runCurrent()
    }
}

class RiveFocusSessionUnitTest : FunSpec({
    test("A traversal key is ignored while the surface is unfocused") {
        runTest {
            val fixture = FocusSessionTestFixture(this)

            fixture.session.onTraversalKey(RiveFocusDirection.Next) shouldBe false

            fixture.traversals.shouldBeEmpty()
        }
    }

    test("A traversal key is consumed before Rive answers") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()

            fixture.session.onTraversalKey(RiveFocusDirection.Next) shouldBe true

            fixture.traversals.map { it.first } shouldContainExactly listOf(RiveFocusDirection.Next)
            fixture.session.traversing shouldBe true
        }
    }

    test("A traversal key enters the tree when Rive holds nothing and nothing was released") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.session.onSurfaceFocusChanged(focused = true)

            fixture.session.onTraversalKey(RiveFocusDirection.Previous) shouldBe true

            fixture.traversals.map { it.first } shouldContainExactly
                listOf(RiveFocusDirection.Previous)
        }
    }

    test("Running out of the tree hands focus to the host in the key's direction") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()

            fixture.session.onTraversalKey(RiveFocusDirection.Previous)
            fixture.completeTraversal(hasFocus = false)

            fixture.handOffs shouldContainExactly listOf(RiveFocusDirection.Previous)
            fixture.session.phase shouldBe FocusPhase.Released
            fixture.session.onTraversalKey(RiveFocusDirection.Previous) shouldBe false
            fixture.traversals shouldHaveSize 1
        }
    }

    test("A stop edge that keeps focus neither releases nor hands off") {
        runTest {
            // A stop keeps focus while reporting no movement. Handing off here would walk focus out of
            // a trap the graphic set.
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()

            fixture.session.onTraversalKey(RiveFocusDirection.Next)
            fixture.completeTraversal(hasFocus = true)

            fixture.handOffs.shouldBeEmpty()
            fixture.session.phase shouldNotBe FocusPhase.Released
            fixture.session.onTraversalKey(RiveFocusDirection.Next) shouldBe true
            fixture.traversals shouldHaveSize 2
        }
    }

    test("Keys pressed during a traversal are consumed without traversing again") {
        runTest {
            // A held key repeats faster than the command server answers. Traversing again from stale
            // state would re-enter the tree at the end instead of leaving it.
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()

            repeat(3) { fixture.session.onTraversalKey(RiveFocusDirection.Next) shouldBe true }
            fixture.completeTraversal(hasFocus = false)

            fixture.traversals shouldHaveSize 1
            fixture.handOffs shouldContainExactly listOf(RiveFocusDirection.Next)
        }
    }

    test("A traversal result that lands after the surface lost focus does not hand off") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()
            fixture.session.onTraversalKey(RiveFocusDirection.Next)

            fixture.session.onSurfaceFocusChanged(focused = false)
            fixture.completeTraversal(hasFocus = false)

            fixture.handOffs.shouldBeEmpty()
        }
    }

    test("Losing surface focus during a traversal clears the focus it may take") {
        runTest {
            // Entering the tree from nothing: the traversal may take focus after the surface lost it,
            // which would leave a ring drawn beside whatever the host focused next.
            val fixture = FocusSessionTestFixture(this)
            fixture.session.onSurfaceFocusChanged(focused = true)
            fixture.session.onTraversalKey(RiveFocusDirection.Next)

            fixture.session.onSurfaceFocusChanged(focused = false)

            verify(exactly = 1) { fixture.clearFocus() }
            fixture.session.traversing shouldBe false
        }
    }

    test("A disposed session does not hand off a traversal that lands afterwards") {
        runTest {
            // The scope outlives a session replaced by a new state machine.
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()
            fixture.session.onTraversalKey(RiveFocusDirection.Next)

            fixture.session.dispose()
            fixture.completeTraversal(hasFocus = false)

            fixture.handOffs.shouldBeEmpty()
            fixture.session.traversing shouldBe false
        }
    }

    test("A traversal that fails hands the key back to the host") {
        runTest {
            // The command server answers a handle it does not know with an error. Swallowing every
            // key on a graphic that cannot traverse would trap keyboard focus on the surface.
            val handOffs = mutableListOf<RiveFocusDirection>()

            val session = RiveFocusSession(
                scope = backgroundScope,
                traverse = { throw RiveStateMachineException("State machine not found") },
                handOff = { direction ->
                    handOffs += direction
                    true
                },
                clearFocus = {},
                requestFrame = {},
            )
            session.onSurfaceFocusChanged(focused = true)

            session.onTraversalKey(RiveFocusDirection.Next) shouldBe true

            session.traversing shouldBe false
            session.phase shouldBe FocusPhase.Released
            handOffs shouldContainExactly listOf(RiveFocusDirection.Next)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    test("A traversal whose answer never arrives stops swallowing keys after its timeout") {
        runTest {
            // Polling stops below RESUMED, so an answer can stall while the window still gets keys.
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()
            fixture.session.onTraversalKey(RiveFocusDirection.Next)

            // A slow answer within the timeout still counts, so keys stay claimed until then.
            advanceTimeBy(900.milliseconds)
            fixture.session.traversing shouldBe true
            advanceTimeBy(200.milliseconds)
            runCurrent()

            fixture.session.traversing shouldBe false
            fixture.handOffs.shouldBeEmpty()
            fixture.session.onTraversalKey(RiveFocusDirection.Next) shouldBe true
            fixture.traversals shouldHaveSize 2
        }
    }

    test("A hand-off the host cannot perform lets the next key re-enter Rive") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.hostCanMoveFocus = false
            fixture.focusedWithRiveFocus()

            fixture.session.onTraversalKey(RiveFocusDirection.Next)
            fixture.completeTraversal(hasFocus = false)

            fixture.session.phase shouldNotBe FocusPhase.Released
            fixture.session.onTraversalKey(RiveFocusDirection.Next) shouldBe true
            fixture.traversals shouldHaveSize 2
        }
    }

    test("Disposing mid-traversal clears the focus it may take") {
        runTest {
            // Turning focus off removes the handler, so nothing else would clear a ring drawn by the
            // traversal already queued.
            val fixture = FocusSessionTestFixture(this)
            fixture.session.onSurfaceFocusChanged(focused = true)
            fixture.session.onTraversalKey(RiveFocusDirection.Next)

            fixture.session.dispose()

            verify(exactly = 1) { fixture.clearFocus() }
        }
    }

    test("Disposing while Rive holds focus clears it") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()

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

    test("Disposing a session that never held any focus leaves the graphic alone") {
        runTest {
            val fixture = FocusSessionTestFixture(this)

            fixture.session.dispose()

            verify(exactly = 0) { fixture.clearFocus() }
        }
    }

    test("Gaining focus by forward traversal enters the tree at once") {
        runTest {
            val fixture = FocusSessionTestFixture(this)

            fixture.session.onSurfaceFocusChanged(
                focused = true,
                entryDirection = RiveFocusDirection.Next
            )
            fixture.completeTraversal(hasFocus = true)

            fixture.traversals.map { it.first } shouldContainExactly listOf(RiveFocusDirection.Next)
            fixture.session.phase shouldBe FocusPhase.Focused
            fixture.handOffs.shouldBeEmpty()
        }
    }

    test("Gaining focus by backward traversal enters the tree from its end") {
        runTest {
            val fixture = FocusSessionTestFixture(this)

            fixture.session.onSurfaceFocusChanged(
                focused = true,
                entryDirection = RiveFocusDirection.Previous
            )

            fixture.traversals.map { it.first } shouldContainExactly
                listOf(RiveFocusDirection.Previous)
        }
    }

    test("Gaining focus without a traversal direction waits for the next key") {
        runTest {
            // A tap focuses the surface, and entering here would move focus off what it tapped.
            val fixture = FocusSessionTestFixture(this)

            fixture.session.onSurfaceFocusChanged(focused = true)

            fixture.traversals.shouldBeEmpty()
            fixture.session.onTraversalKey(RiveFocusDirection.Next) shouldBe true
            fixture.traversals shouldHaveSize 1
        }
    }

    test("Gaining focus while Rive already holds a node leaves it alone") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.session.onRiveFocusChanged(hasFocus = true)

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
            fixture.session.phase shouldNotBe FocusPhase.Released
            fixture.session.onTraversalKey(RiveFocusDirection.Next) shouldBe true
            fixture.traversals shouldHaveSize 2
        }
    }

    test("Gaining surface focus clears a previous release") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()
            fixture.session.onTraversalKey(RiveFocusDirection.Next)
            fixture.completeTraversal(hasFocus = false)
            fixture.session.phase shouldBe FocusPhase.Released

            fixture.session.onSurfaceFocusChanged(focused = false)
            fixture.session.onSurfaceFocusChanged(focused = true)

            fixture.session.phase shouldNotBe FocusPhase.Released
        }
    }

    test("Rive taking focus on its own clears a previous release") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()
            fixture.session.onTraversalKey(RiveFocusDirection.Next)
            fixture.completeTraversal(hasFocus = false)
            fixture.session.phase shouldBe FocusPhase.Released

            fixture.session.onRiveFocusChanged(hasFocus = true)

            fixture.session.phase shouldNotBe FocusPhase.Released
        }
    }

    test("Rive dropping focus on its own releases the session") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()

            fixture.session.onRiveFocusChanged(hasFocus = false)

            fixture.session.phase shouldBe FocusPhase.Released
        }
    }

    test("Repeating the same polled focus value changes nothing") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()
            fixture.session.onRiveFocusChanged(hasFocus = false)
            fixture.session.phase shouldBe FocusPhase.Released

            // Acting on the value rather than the edge would clear the release here.
            fixture.session.onRiveFocusChanged(hasFocus = false)

            fixture.session.phase shouldBe FocusPhase.Released
        }
    }

    test("Losing surface focus stops the session handling keys") {
        runTest {
            val fixture = FocusSessionTestFixture(this)
            fixture.focusedWithRiveFocus()

            fixture.session.onSurfaceFocusChanged(focused = false)

            fixture.session.onTraversalKey(RiveFocusDirection.Next) shouldBe false
            fixture.traversals.shouldBeEmpty()
        }
    }

    test("Losing surface focus drops focus the graphic still holds") {
        runTest {
            // Otherwise the graphic keeps drawing its ring beside whatever the host focused next.
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
            // A tap focuses the surface and a node together, and the poll reports the node later.
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

    test("A traversal asks for a frame even when it releases to the host") {
        runTest {
            // A paused graphic draws once, so the ring would survive the focus that justified it.
            val fixture = FocusSessionTestFixture(this)
            fixture.session.onSurfaceFocusChanged(focused = true)

            fixture.session.onTraversalKey(RiveFocusDirection.Next)
            fixture.completeTraversal(hasFocus = false)

            verify(exactly = 1) { fixture.requestFrame() }
        }
    }
})
