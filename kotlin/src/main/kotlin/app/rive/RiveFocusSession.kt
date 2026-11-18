package app.rive

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Routes key events into a Rive instance and transfers Android input focus to the surrounding UI
 * when navigation leaves the instance.
 *
 * Android key event handlers must immediately report whether they consumed a keypress, but Rive
 * processes keys and focus moves asynchronously. So the session decides at once from what it
 * already knows, then finishes once Rive answers:
 * - `Tab` and `Shift+Tab` are consumed, and so are unmodified arrow keys while an element holds
 *   focus. The focused element gets them first. If it doesn't handle them, focus moves, and if
 *   focus leaves the instance, Android input focus moves to the surrounding UI in the same
 *   direction.
 * - Other keys reach the focused element, and are consumed only when it expects keyboard input.
 *   Ctrl and Meta shortcuts are never consumed, so the app's own shortcuts keep working.
 * - Keys pressed while a navigation is in flight are consumed, apart from shortcuts, and wait for
 *   it to finish. Then they're handled in order: a new press of a navigation key navigates as it
 *   would have without the wait, and other keys go to the focused element. Keys with nowhere to go
 *   are dropped. A repeat that waited never navigates, so a held `Tab` at the end of the instance
 *   leaves it instead of wrapping back in.
 * - A key release reaches Rive only if a press of that key did, and is consumed only if its first
 *   press was.
 *
 * Keys pass through while the host surface doesn't hold Android input focus. Outside a navigation,
 * they also pass through while no element inside the instance holds focus, except `Tab` and
 * `Shift+Tab`, which enter the instance, even after Rive clears focus on its own.
 *
 * @param scope Runs key deliveries and focus moves. Cancelling it abandons any still awaiting their
 *    result.
 * @param move Moves Rive focus, suspending until the outcome arrives.
 * @param sendKey Delivers a key to the focused element, suspending until it reports whether it
 *    handled the key.
 * @param handOff Moves Android input focus past the surface once navigation leaves the instance.
 * @param clearFocus Drops any focus the Rive instance still holds.
 * @param requestFrame Renders a frame. A paused instance draws once, so nothing is visible without
 *    it.
 */
internal class RiveFocusSession(
    private val scope: CoroutineScope,
    private val move: suspend (direction: RiveFocusDirection) -> RiveFocusMoveResult,
    private val sendKey: suspend (press: RiveKeyPress) -> Boolean,
    private val handOff: (direction: RiveFocusDirection) -> Unit,
    private val clearFocus: () -> Unit,
    private val requestFrame: () -> Unit,
) {
    /** Whether a Rive node holds focus, as far as the session knows. */
    internal enum class FocusPhase {
        /** No Rive node holds focus, and the next `Tab` or `Shift+Tab` enters the instance. */
        Ready,

        /** The latest reported Rive state has a focused node. */
        Focused,
    }

    /**
     * How a held key was handled, so its release can go the same way.
     *
     * Key events that wait for a navigation share this record, so a release queued before its press
     * is handled still follows that press.
     *
     * @param consumed Whether the host consumes the key, decided at its first press.
     */
    private class HeldKey(val consumed: Boolean) {
        /** Whether a press of the key reached the focused element, the first one or a repeat. */
        var delivered = false
            private set

        /** Records that a press of the key reached the focused element. */
        fun markDelivered() {
            delivered = true
        }
    }

    /**
     * A key event waiting for the navigation in flight.
     *
     * @param press The key event.
     * @param held How the key was handled so far, shared with the key's other events.
     */
    private class AwaitingKey(val press: RiveKeyPress, val held: HeldKey)

    /** How a navigation ended. A navigation that timed out has no outcome. */
    private sealed interface NavigationOutcome {
        /** The focused element handled the offered key, so focus didn't move. */
        data object KeyHandled : NavigationOutcome

        /**
         * Focus moved, or tried to.
         *
         * @param result The outcome of the move.
         */
        data class Moved(val result: RiveFocusMoveResult) : NavigationOutcome
    }

    /** Whether the host surface currently holds Android input focus. */
    var surfaceFocused: Boolean = false
        private set

    /** The current focus phase. */
    var phase: FocusPhase = FocusPhase.Ready
        private set

    /** The latest focus state Rive reported, from a poll or a move. */
    private var riveFocusState: RiveFocusState = RiveFocusState.Unfocused

    /** Presses the session consumed or delivered, by Android key code. */
    private val heldKeys = mutableMapOf<Int, HeldKey>()

    /** Key events that arrived during the navigation in flight, handled in order once it lands. */
    private val keysAwaitingNavigation = mutableListOf<AwaitingKey>()

    /** The latest navigation. Use [traversing] to check whether it is still running. */
    private var traversal: Job? = null

    /** Whether a navigation is awaiting its result. */
    val traversing: Boolean
        get() = traversal?.isActive == true

    /**
     * Records a change in the host surface's Android input focus.
     *
     * Gaining focus starts a new session and, when navigation brought focus here, enters the Rive
     * instance unless it already holds a node. An entry that finds nothing stays on the surface, so
     * a lone surface cannot loop. Losing focus always clears Rive focus, since a tap can focus a
     * node before the poll reports it.
     *
     * @param focused Whether the surface now holds Android input focus.
     * @param entryDirection The direction to enter in, or null when focus arrived another way, such
     *    as a tap.
     */
    fun onSurfaceFocusChanged(focused: Boolean, entryDirection: RiveFocusDirection? = null) {
        if (focused == surfaceFocused) {
            return
        }
        surfaceFocused = focused
        if (focused) {
            if (entryDirection != null && phase != FocusPhase.Focused) {
                startNavigation(entryDirection, offered = null, entering = true)
            }
            return
        }
        heldKeys.clear()
        dropFocus(force = true)
    }

    /**
     * Records the latest focus state from [StateMachine.focusState], which polls and focus moves
     * update.
     *
     * @param focusState The focus state Rive reported.
     */
    fun onRiveFocusChanged(focusState: RiveFocusState) {
        applyFocusState(focusState)
    }

    /**
     * Handles a key event the host received.
     *
     * @param press The translated key event.
     * @return true when the caller should consume the event.
     */
    fun onKeyEvent(press: RiveKeyPress): Boolean {
        if (!press.isPressed) {
            val held = heldKeys.remove(press.keyCode) ?: return false
            when {
                // Held in order with the other keys. Its press may still be waiting, so resume
                // decides whether to send it.
                traversing -> keysAwaitingNavigation += AwaitingKey(press, held)

                held.delivered -> deliver(press)
            }
            return held.consumed
        }
        if (!surfaceFocused) {
            return false
        }
        val navigation = press.navigation
        val holdsNode = phase == FocusPhase.Focused
        val handled = when {
            // Held until the navigation lands, then handled in order; see resume.
            traversing -> true

            // Tab enters the instance when no element holds focus.
            navigation.isSequential() -> true

            // Every other key needs a focused element to go to.
            else -> holdsNode
        }
        if (!handled) {
            return false
        }
        // A repeat shares the record of its first press.
        val held = heldKeys.getOrPut(press.keyCode) {
            val consumed = when {
                press.isChord -> false
                navigation != null || traversing -> true
                else -> (riveFocusState as? RiveFocusState.Focused)?.expectsKeyboardInput == true
            }
            HeldKey(consumed)
        }
        if (traversing) {
            keysAwaitingNavigation += AwaitingKey(press, held)
        } else {
            handlePress(press, held, navigation, holdsNode)
        }
        return held.consumed
    }

    /**
     * Ends the session's claim on focus when it is replaced or focus handling is turned off, so a
     * late result cannot move Android input focus and no focus ring is left behind.
     */
    fun dispose() {
        dropFocus(force = surfaceFocused)
    }

    /** Delivers [press] to the focused element without waiting for its answer. */
    private fun deliver(press: RiveKeyPress) {
        // Undispatched, so keys reach Rive in the order they were pressed.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                sendKey(press)
            } catch (e: CancellationException) {
                // Cancellation isn't a failure, so rethrow it and end the coroutine as cancelled.
                throw e
            } catch (_: Exception) {
                // An instance that can't take the key simply doesn't react to it.
            }
        }
        // A paused instance draws only on request, and the key may have changed what it shows.
        requestFrame()
    }

    /**
     * Offers a key to the focused element, moves focus when it goes unhandled, and applies the
     * outcome once it arrives.
     *
     * @param direction The direction to move focus in.
     * @param offered The key to offer the focused element first, or null to move straight away.
     * @param entering Whether this is the move that enters the instance as the surface gains focus.
     */
    private fun startNavigation(
        direction: RiveFocusDirection,
        offered: RiveKeyPress?,
        entering: Boolean,
    ) {
        // Undispatched, so the offer or move is submitted before any later key.
        traversal = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val outcome = try {
                withTimeoutOrNull(TRAVERSAL_TIMEOUT) {
                    if (offered != null && sendKey(offered)) {
                        NavigationOutcome.KeyHandled
                    } else {
                        NavigationOutcome.Moved(move(direction))
                    }
                }
            } catch (e: CancellationException) {
                // Cancellation isn't a failure. Rethrow it, or the catch below would hand off focus.
                throw e
            } catch (_: Exception) {
                // An instance that cannot move focus holds none. Hand off rather than trap keys.
                NavigationOutcome.Moved(
                    RiveFocusMoveResult(moved = false, focusState = RiveFocusState.Unfocused)
                )
            }
            // Cleared before handing off. The hand-off can take surface focus away and give it
            // back, which starts a new navigation that must stay tracked.
            traversal = null
            when (outcome) {
                // The element used the key, so draw whatever it changed.
                NavigationOutcome.KeyHandled -> requestFrame()

                // A delayed reply must not leave a navigation in flight indefinitely. Don't wait,
                // so the next key starts a new navigation instead of waiting behind this one.
                null -> Unit

                is NavigationOutcome.Moved -> if (entering) {
                    onEntered(outcome.result)
                } else {
                    onNavigated(direction, outcome.result)
                }
            }
            // Sent after the move, in order, so they reach the element it landed on.
            val awaiting = keysAwaitingNavigation.toList()
            keysAwaitingNavigation.clear()
            awaiting.forEach(::resume)
        }
    }

    /**
     * Handles a key that waited for a navigation, now that the navigation has landed.
     *
     * A new press of a navigation key starts its own navigation, as it would have without the
     * wait, and the keys after it wait for that one. Other keys go to the focused element. A key
     * that reaches no element is dropped. Its release is sent only if another press of the same key
     * reached an element.
     *
     * @param key The key event that waited.
     */
    private fun resume(key: AwaitingKey) {
        if (traversing) {
            // An earlier waiting key started a navigation, so this one waits for that.
            keysAwaitingNavigation += key
            return
        }
        val press = key.press
        val holdsNode = surfaceFocused && phase == FocusPhase.Focused
        if (!press.isPressed) {
            // A press that waited may have only entered the instance, or reached no element.
            if (holdsNode && key.held.delivered) {
                deliver(press)
            }
            return
        }
        // The same rules as a fresh press in onKeyEvent. A repeat that waited never navigates.
        val navigation = press.navigation?.takeIf { direction ->
            val canNavigate = if (direction.isSequential()) {
                surfaceFocused
            } else {
                holdsNode
            }
            !press.isRepeat && canNavigate
        }
        handlePress(press, key.held, navigation, holdsNode)
    }

    /**
     * Delivers a press to the focused element, or starts the navigation it asks for, offering the
     * press to the focused element first if there is one. A press with nowhere to go is dropped.
     *
     * @param press The press to handle.
     * @param held How the key was handled so far. Marked when the press reaches an element.
     * @param navigation The direction the press moves focus in, or null if it doesn't move focus.
     * @param holdsNode Whether an element inside the instance holds focus.
     */
    private fun handlePress(
        press: RiveKeyPress,
        held: HeldKey,
        navigation: RiveFocusDirection?,
        holdsNode: Boolean,
    ) {
        if (holdsNode) {
            // A repeat counts too. It can reach the element even when the first press only
            // entered the instance.
            held.markDelivered()
        }
        when {
            navigation != null ->
                startNavigation(navigation, offered = press.takeIf { holdsNode }, entering = false)

            holdsNode -> deliver(press)
        }
    }

    /**
     * Applies the outcome of entering the instance as the surface gained focus.
     *
     * @param result The outcome of the entering move.
     */
    private fun onEntered(result: RiveFocusMoveResult) {
        applyFocusState(result.focusState)
        requestFrame()
    }

    /**
     * Applies the outcome of a navigation key, handing focus to the surrounding UI when it left the
     * instance.
     *
     * Sequential traversal leaves when Rive no longer holds focus; a stop edge keeps focus while
     * reporting no movement. A spatial move ignores edge behavior, so it leaves whenever it could
     * not move. If Android can't move focus either, the surface keeps it, and the next key goes back
     * into the instance.
     *
     * @param direction The direction the key moved in.
     * @param result The outcome of the move.
     */
    private fun onNavigated(direction: RiveFocusDirection, result: RiveFocusMoveResult) {
        applyFocusState(result.focusState)
        val left = if (direction.isSequential()) {
            result.focusState == RiveFocusState.Unfocused
        } else {
            !result.moved
        }
        // Leaving needs a frame too, so the ring goes away with the focus.
        requestFrame()
        if (left && surfaceFocused) {
            handOff(direction)
        }
    }

    /**
     * Records a focus state Rive reported, from a poll or a move.
     *
     * @param focusState The reported focus state.
     */
    private fun applyFocusState(focusState: RiveFocusState) {
        riveFocusState = focusState
        phase = if (focusState is RiveFocusState.Focused) FocusPhase.Focused else FocusPhase.Ready
    }

    /**
     * Abandons any navigation in flight and clears focus the instance holds or may be about to
     * take.
     *
     * The clear is queued behind an abandoned navigation's command, so it wins.
     *
     * @param force Clears even when Rive appears to hold nothing, for focus not yet polled.
     *    Callers force the clear whenever the surface holds focus, which every navigation needs,
     *    so this also clears focus a navigation in flight may still take.
     */
    private fun dropFocus(force: Boolean) {
        traversal?.cancel()
        keysAwaitingNavigation.clear()
        if (force || phase == FocusPhase.Focused) {
            clearFocus()
            if (phase == FocusPhase.Focused) {
                phase = FocusPhase.Ready
            }
            requestFrame()
        }
    }

    /** Whether this is a sequential traversal direction rather than a spatial one. */
    private fun RiveFocusDirection?.isSequential(): Boolean =
        this == RiveFocusDirection.Next || this == RiveFocusDirection.Previous

    companion object {
        /** How long a navigation waits for Rive to answer its key offer and focus move. */
        private val TRAVERSAL_TIMEOUT = 1.seconds
    }
}
