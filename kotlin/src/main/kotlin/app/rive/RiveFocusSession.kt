package app.rive

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tracks focus traversal requests within a Rive instance and transfers Android input focus to the
 * surrounding UI when traversal leaves the instance.
 *
 * Android key event handlers must immediately report whether they consumed a keypress, but Rive
 * processes focus traversal asynchronously. While the surface holds Android input focus and the
 * session has not released focus, the handler consumes traversal keypresses without waiting for
 * Rive's response.
 *
 * If the response indicates that traversal left the Rive instance, the session moves Android input
 * focus to the surrounding UI. The original keypress remains consumed.
 *
 * @param scope Runs traversals. Cancelling it abandons any traversal still awaiting its result.
 * @param traverse Moves Rive focus, suspending until the resulting focus state arrives.
 * @param handOff Moves Android input focus past the surface once traversal leaves the instance.
 *    Returns whether focus moved.
 * @param clearFocus Drops any focus the Rive instance still holds.
 * @param requestFrame Renders a frame. A paused instance draws once, so nothing is visible without
 *    it.
 */
internal class RiveFocusSession(
    private val scope: CoroutineScope,
    private val traverse: suspend (direction: RiveFocusDirection) -> RiveFocusState,
    private val handOff: (direction: RiveFocusDirection) -> Boolean,
    private val clearFocus: () -> Unit,
    private val requestFrame: () -> Unit,
) {
    /** The session's focus phase, distinguishing readiness to enter Rive from having left it. */
    internal enum class FocusPhase {
        /** No Rive node holds focus, and the next traversal request may enter the instance. */
        Ready,

        /** The latest reported Rive state has a focused node. */
        Focused,

        /** Rive has released focus, so subsequent traversal keypresses pass through to Android. */
        Released,
    }

    /** Whether the host surface currently holds Android input focus. */
    var surfaceFocused: Boolean = false
        private set

    /** The current focus phase. */
    var phase: FocusPhase = FocusPhase.Ready
        private set

    /** The latest traversal. Only [traversing] decides whether it is still in flight. */
    private var traversal: Job? = null

    /** Whether a traversal is awaiting its result. */
    val traversing: Boolean
        get() = traversal?.isActive == true

    /**
     * Records a change in the host surface's Android input focus.
     *
     * Gaining focus starts a new session and, when traversal brought focus here, enters the Rive
     * instance unless it already holds a node. An entry that finds nothing stays on the surface, so
     * a lone surface cannot loop. Losing focus always clears Rive focus, since a tap can focus a
     * node before the poll reports it.
     *
     * @param focused Whether the surface now holds Android input focus.
     * @param entryDirection The direction traversal arrived in, or null when focus arrived another
     *    way, such as a tap.
     */
    fun onSurfaceFocusChanged(focused: Boolean, entryDirection: RiveFocusDirection? = null) {
        if (focused == surfaceFocused) {
            return
        }
        surfaceFocused = focused
        if (focused) {
            if (phase == FocusPhase.Released) {
                phase = FocusPhase.Ready
            }
            if (entryDirection != null && phase != FocusPhase.Focused) {
                startTraversal(entryDirection, entering = true)
            }
            return
        }
        dropFocus(force = true)
    }

    /**
     * Records the focus state most recently polled from Rive.
     *
     * Acts on the transition, not the value, so an instance that keeps holding focus does not
     * restart the session.
     *
     * @param hasFocus Whether Rive currently holds focus on an element inside the instance.
     */
    fun onRiveFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            phase = FocusPhase.Focused
        } else if (phase == FocusPhase.Focused) {
            phase = FocusPhase.Released
        }
    }

    /**
     * Handles a traversal key.
     *
     * Consumes the key without waiting for Rive. Keys pressed while a traversal is in flight are
     * consumed without traversing again, so a held key at the end of the tree leaves it rather
     * than re-entering.
     *
     * @param direction The direction the key moves focus in.
     * @return true when the caller should consume the event.
     */
    fun onTraversalKey(direction: RiveFocusDirection): Boolean {
        if (!surfaceFocused || phase == FocusPhase.Released) {
            return false
        }
        if (traversing) {
            return true
        }
        startTraversal(direction, entering = false)
        return true
    }

    /**
     * Ends the session's claim on focus when it is replaced or focus handling is turned off, so a
     * late result cannot move Android input focus and no focus ring is left behind.
     */
    fun dispose() {
        dropFocus(force = surfaceFocused)
    }

    /**
     * Moves Rive focus one step and applies the result once it arrives.
     *
     * @param direction The direction to move focus in.
     * @param entering Whether this is the traversal that enters the instance on arrival.
     */
    private fun startTraversal(direction: RiveFocusDirection, entering: Boolean) {
        // Undispatched, so the command is submitted before the caller returns.
        traversal = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val focusState = try {
                withTimeoutOrNull(TRAVERSAL_TIMEOUT) { traverse(direction) }
            } catch (e: CancellationException) {
                // A cancelled traversal must stop here rather than hand off a result.
                throw e
            } catch (_: Exception) {
                // An instance that cannot traverse holds no focus. Hand off rather than trap keys.
                RiveFocusState.Unfocused
            }
            // Cleared before handing off, whose synchronous focus loss must not see it in flight.
            traversal = null
            if (focusState == null) {
                // Polling stops below RESUMED, so the answer may be late. Stop waiting, so the next
                // key starts a new traversal instead of being dropped.
                return@launch
            }
            if (entering) {
                phase = when {
                    focusState is RiveFocusState.Focused -> FocusPhase.Focused
                    phase == FocusPhase.Focused -> FocusPhase.Ready
                    else -> phase
                }
                requestFrame()
            } else {
                onTraversalResult(direction, focusState)
            }
        }
    }

    /**
     * Abandons any traversal in flight and clears focus the instance holds or may be about to take.
     *
     * The clear is queued behind an abandoned traversal's command, so it wins.
     *
     * @param force Clears even when Rive appears to hold nothing, for focus not yet polled.
     */
    private fun dropFocus(force: Boolean) {
        val wasTraversing = traversing
        traversal?.cancel()
        if (force || phase == FocusPhase.Focused || wasTraversing) {
            clearFocus()
            if (phase == FocusPhase.Focused) {
                phase = FocusPhase.Ready
            }
            requestFrame()
        }
    }

    /**
     * Applies the focus state a traversal left behind.
     *
     * Decided by whether Rive still holds focus afterwards. Movement alone gets a stop wrong,
     * which keeps focus while reporting none.
     *
     * @param direction The direction the traversal moved in.
     * @param focusState The focus state the traversal left behind.
     */
    private fun onTraversalResult(direction: RiveFocusDirection, focusState: RiveFocusState) {
        phase = when (focusState) {
            is RiveFocusState.Focused -> FocusPhase.Focused
            RiveFocusState.Unfocused -> FocusPhase.Released
        }
        // Releasing needs a frame too, so the ring goes away with the focus.
        requestFrame()
        if (phase == FocusPhase.Released && surfaceFocused && !handOff(direction)) {
            // Android could not move focus, so let the next key re-enter Rive.
            phase = FocusPhase.Ready
        }
    }

    companion object {
        /**
         * How long a traversal waits for its result. Far above a normal round trip of a few
         * milliseconds.
         */
        private val TRAVERSAL_TIMEOUT = 1.seconds
    }
}
