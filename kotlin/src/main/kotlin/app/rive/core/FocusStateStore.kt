package app.rive.core

import app.rive.RiveFocusState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Extends a [CommandQueue] with durable focus state for its registered state machines.
 *
 * Native focus callbacks are transient messages delivered when the command queue is polled. This
 * store captures their latest accepted value so a [StateMachine][app.rive.StateMachine] can expose
 * focus state without collecting an event flow for its entire lifetime.
 *
 * @param reserveNextRequestID Atomically reserves the next ID from the owning worker's shared,
 *    monotonically increasing request sequence.
 */
internal class FocusStateStore(private val reserveNextRequestID: () -> Long) {
    /**
     * Durable focus state for one registered state machine.
     *
     * [requestIDBoundary] is the request ID reserved when the state machine was registered or a
     * focus-changing command was last submitted. A focus-state callback is current only when its
     * request ID is greater than this boundary, which discards answers to polls that were already
     * in flight when the boundary moved.
     *
     * @param requestIDBoundary The request ID before which focus callbacks are stale.
     */
    private class Slot(var requestIDBoundary: Long) {
        val mutableFocusState = MutableStateFlow(RiveFocusState())
        val focusState = mutableFocusState.asStateFlow()
        val mutableHasFocusNodes = MutableStateFlow(false)
        val hasFocusNodes = mutableHasFocusNodes.asStateFlow()

        /** Leaves retained flows in the state a deleted state machine must report. */
        fun publishTerminalState() {
            mutableFocusState.value = RiveFocusState()
            mutableHasFocusNodes.value = false
        }
    }

    /**
     * Serializes registration, teardown, and focus callbacks.
     *
     * Polling delivers JNI callbacks on the polling caller's thread, while registration and
     * resource teardown do not enforce that same caller. Keeping lookup and publication in one
     * critical section lets membership in [slots] fully define whether a state machine is
     * registered, without a separate tombstone flag.
     */
    private val lock = Any()

    /** Focus state owned by each registered state machine. Access only while holding [lock]. */
    private val slots = mutableMapOf<StateMachineHandle, Slot>()

    /**
     * Registers a newly created state machine for durable focus-state tracking.
     *
     * @param stateMachineHandle The newly created state machine.
     * @throws IllegalStateException If the handle is already registered.
     */
    fun register(stateMachineHandle: StateMachineHandle) = synchronized(lock) {
        check(stateMachineHandle !in slots) {
            "Focus state is already registered for $stateMachineHandle"
        }
        slots[stateMachineHandle] = Slot(reserveNextRequestID())
    }

    /**
     * Unregisters a deleted state machine and discards its durable focus state.
     *
     * Before removal, this publishes the terminal value for any observer that retains the state
     * flows: a deleted state machine can hold neither focus nor focusable content, so a host
     * observing focus state sees the session end rather than a stale focused value. Focus callbacks
     * already in flight are ignored once this returns because they consult the same locked registry.
     *
     * Does nothing if the state machine is already unregistered.
     *
     * @param stateMachineHandle The state machine being deleted.
     */
    fun unregister(stateMachineHandle: StateMachineHandle): Unit = synchronized(lock) {
        slots.remove(stateMachineHandle)?.publishTerminalState()
    }

    /**
     * Unregisters every state machine when the owning worker is disposed.
     *
     * Each retained state flow receives the same terminal value as [unregister].
     */
    fun clear() = synchronized(lock) {
        slots.values.forEach(Slot::publishTerminalState)
        slots.clear()
    }

    /**
     * Returns the durable focus state for a registered state machine.
     *
     * @param stateMachineHandle The state machine whose state should be observed.
     * @return A flow containing the latest accepted focus state.
     * @throws IllegalStateException If the handle is not registered.
     */
    fun focusState(stateMachineHandle: StateMachineHandle): StateFlow<RiveFocusState> =
        synchronized(lock) { requireSlot(stateMachineHandle).focusState }

    /**
     * Returns whether a registered state machine's graphic contains any focusable content.
     *
     * @param stateMachineHandle The state machine whose content should be observed.
     * @return A flow containing the latest reported value.
     * @throws IllegalStateException If the handle is not registered.
     */
    fun hasFocusNodes(stateMachineHandle: StateMachineHandle): StateFlow<Boolean> =
        synchronized(lock) { requireSlot(stateMachineHandle).hasFocusNodes }

    /**
     * Applies a native has-focus-nodes callback.
     *
     * This value is not subject to the [invalidate] boundary: a focus move does not change whether
     * the graphic contains focusable content, and the command queue's single message stream
     * delivers these answers in submission order, so a later answer is always the newer one.
     *
     * @param stateMachineHandle The state machine reported by native code.
     * @param hasFocusNodes Whether the unified focus tree holds any focusable node.
     */
    fun applyHasFocusNodes(stateMachineHandle: StateMachineHandle, hasFocusNodes: Boolean) =
        synchronized(lock) {
            val slot = slots[stateMachineHandle] ?: return@synchronized
            slot.mutableHasFocusNodes.value = hasFocusNodes
        }

    /**
     * Begins a new focus generation for a state machine.
     *
     * Called when a focus-changing command is submitted. Reserving and storing a new request ID
     * boundary makes answers to polls that were already in flight stale, so an observer cannot read
     * pre-command focus state as though it reflected the command.
     *
     * Call this only after the command is enqueued. Request IDs increase monotonically, so a poll
     * whose ID is above the boundary reserved that ID, and was then enqueued, after the command;
     * the command server answers it with post-command state. Reserving the boundary first would
     * let a poll from another thread take an ID above it yet reach the server ahead of the
     * command. A poll that races into the window between enqueueing and this call is discarded
     * even though its answer is current, which is safe because the command's own follow-up poll
     * always reports the result.
     *
     * Unregistered state machines are ignored: a focus command can reach the queue while its state
     * machine is being deleted, and there is no state left to invalidate.
     *
     * @param stateMachineHandle The state machine whose focus state is about to change.
     */
    fun invalidate(stateMachineHandle: StateMachineHandle) = synchronized(lock) {
        val slot = slots[stateMachineHandle] ?: return@synchronized
        slot.requestIDBoundary = reserveNextRequestID()
    }

    /**
     * Applies a native focus-state callback when it belongs to the current generation.
     *
     * Callbacks for deleted or otherwise unknown state machines are ignored because native work
     * already in flight may complete after the corresponding Kotlin resource has closed.
     *
     * @param requestID The worker request that produced this callback.
     * @param stateMachineHandle The state machine reported by native code.
     * @param hasFocus Whether Rive holds focus on an element inside the graphic.
     * @param expectsKeyboardInput Whether the focused element accepts key and text input.
     */
    fun applyFocusState(
        requestID: Long,
        stateMachineHandle: StateMachineHandle,
        hasFocus: Boolean,
        expectsKeyboardInput: Boolean,
    ) = synchronized(lock) {
        val slot = slots[stateMachineHandle] ?: return@synchronized
        if (requestID <= slot.requestIDBoundary) {
            return@synchronized
        }
        slot.mutableFocusState.value = RiveFocusState(hasFocus, expectsKeyboardInput)
    }

    /**
     * Finds the durable state for a registered state machine.
     *
     * The caller must hold [lock] so the slot remains registered for the caller's complete
     * operation.
     *
     * @param stateMachineHandle The state machine whose slot is required.
     * @return The corresponding durable state.
     * @throws IllegalStateException If the handle is not registered.
     */
    private fun requireSlot(stateMachineHandle: StateMachineHandle): Slot =
        checkNotNull(slots[stateMachineHandle]) {
            "No focus state is registered for $stateMachineHandle"
        }
}
