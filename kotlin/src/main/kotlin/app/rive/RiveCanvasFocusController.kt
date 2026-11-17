package app.rive

import android.view.KeyEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Resolves a mutable [RiveFocusMode] against a Rive instance's focusable content for the host view
 * of a [RiveCanvasSession].
 *
 * [focusability] updates as soon as [mode] is set, and after [hasFocusNodes] reports a new value.
 * A [RiveFocusSession] exists only while [focusability] is not [RiveFocusability.None]. It starts
 * from the host view's current focus, and disposing it clears Rive focus.
 *
 * The host forwards its focus changes and key events. The controller never changes view
 * properties. Call members on the main thread.
 *
 * @param initialMode Initial focus mode.
 * @param stateMachine State machine whose focus is traversed, polled, and cleared.
 * @param hasFocusNodes Whether the Rive instance has focusable content, or null until the first
 *    answer arrives.
 */
internal class RiveCanvasFocusController(
    initialMode: RiveFocusMode,
    private val stateMachine: StateMachine,
    private val hasFocusNodes: StateFlow<Boolean?>,
) : AutoCloseable {
    /** Runs traversals and observes focus reports. Cancelled on [close]. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _focusability = MutableStateFlow(RiveFocusability.None)

    /** Resolved focusability for the host view. */
    val focusability: StateFlow<RiveFocusability> = _focusability.asStateFlow()

    /** Host view while it holds Android input focus, used to hand focus onward. */
    private var focusedHost: View? = null

    /** Current focus session, or null while [focusability] is [RiveFocusability.None]. */
    private var session: RiveFocusSession? = null

    /**
     * Current focus mode. Setting the current mode does nothing.
     *
     * @throws RiveResourceClosedException If focus is turned on after the state machine has been
     *    closed.
     */
    var mode: RiveFocusMode = initialMode
        set(value) {
            if (field == value) {
                return
            }
            if (value != RiveFocusMode.Off) {
                // A settled instance never polls, so wake it to report whether it has focus nodes.
                // Done first, so a closed state machine throws before any state changes.
                stateMachine.unsettle()
            }
            field = value
            updateFocusability()
        }

    init {
        // The collector below first runs on a later message, and a host reads focusability at once.
        updateFocusability()
        // Not immediate: the worker publishes this while holding a lock, and the update can dispose
        // the session and run host code.
        scope.launch(Dispatchers.Main) { hasFocusNodes.collect { updateFocusability() } }
        // Only the poll can report focus the Rive instance moved on its own.
        scope.launch {
            stateMachine.focusState.collect { focusState ->
                session?.onRiveFocusChanged(focusState is RiveFocusState.Focused)
            }
        }
    }

    /**
     * Polls focus state after an advance, so focus changes the advance made are observed. Does
     * nothing while [mode] is [RiveFocusMode.Off].
     */
    fun onAdvanced() {
        if (mode == RiveFocusMode.Off) return
        stateMachine.pollFocus()
    }

    /**
     * Records a change in the host view's Android input focus.
     *
     * @param host View that gained or lost focus.
     * @param gainFocus Whether the host gained focus.
     * @param direction Direction focus moved in, as passed to [View.onFocusChanged].
     */
    fun onFocusChanged(host: View, gainFocus: Boolean, direction: Int) {
        focusedHost = host.takeIf { gainFocus }
        session?.onSurfaceFocusChanged(
            focused = gainFocus,
            entryDirection = when (direction) {
                View.FOCUS_FORWARD -> RiveFocusDirection.Next
                View.FOCUS_BACKWARD -> RiveFocusDirection.Previous
                else -> null
            },
        )
    }

    /**
     * Starts a traversal for `Tab` or `Shift+Tab`.
     *
     * @param event Key event the host received.
     * @return Whether the event was consumed.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val direction = event.traversalDirection() ?: return false
        return session?.onTraversalKey(direction) ?: false
    }

    /**
     * Clears any Rive focus, reports [RiveFocusability.None] so the host view gives up Android
     * input focus, and stops observing focus state.
     */
    override fun close() {
        try {
            // Runs before the scope is canceled, so it still sees a traversal in flight.
            session?.dispose()
        } finally {
            session = null
            focusedHost = null
            scope.cancel()
            _focusability.value = RiveFocusability.None
        }
    }

    /**
     * Resolves [mode] against the latest reported focus nodes, creating or disposing the session to
     * match.
     */
    private fun updateFocusability() {
        val focusability = mode.focusability(hasFocusNodes.value)
        _focusability.value = focusability
        val focusable = focusability != RiveFocusability.None
        if (focusable == (session != null)) {
            return
        }
        // Disposing the old session stops a late traversal result from moving Android input focus.
        session?.dispose()
        session = if (!focusable) {
            null
        } else {
            RiveFocusSession(
                scope = scope,
                traverse = stateMachine::moveFocus,
                // The key that left the tree was consumed, so move focus as the key would have.
                handOff = { direction ->
                    val androidDirection = when (direction) {
                        RiveFocusDirection.Next -> View.FOCUS_FORWARD
                        RiveFocusDirection.Previous -> View.FOCUS_BACKWARD
                    }
                    val host = focusedHost
                    val next = host?.focusSearch(androidDirection)
                    // If the only view found is the host itself, focus has nowhere to go. Report
                    // that it didn't move, so the next keypress goes back into the instance.
                    if (next == null || next === host) {
                        false
                    } else {
                        next.requestFocus(androidDirection)
                    }
                },
                clearFocus = stateMachine::clearFocusForLifecycleCleanup,
                requestFrame = {
                    try {
                        // The render loop draws whenever the state machine is unsettled.
                        stateMachine.unsettle()
                    } catch (_: RiveResourceClosedException) {
                        // A closed state machine has nothing left to draw.
                    }
                },
            ).apply {
                // The host may already hold focus, and it reports focus only when it changes.
                onSurfaceFocusChanged(focusedHost != null)
                onRiveFocusChanged(stateMachine.focusState.value is RiveFocusState.Focused)
            }
        }
    }
}
