package app.rive.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Extends a [CommandQueue] with durable settled state for its registered state machines.
 *
 * Native settled callbacks are transient messages delivered when the command queue is polled. This
 * store captures their latest accepted value so a [StateMachine][app.rive.StateMachine] can expose
 * settled state without collecting an event flow for its entire lifetime. Keeping this as a direct
 * extension of the command queue also keeps its request IDs out of the higher-level API.
 *
 * Each state machine records the request ID reserved when it last became unsettled. That request ID
 * is its boundary: settled callbacks at or before it are stale, while callbacks after it belong to
 * the current generation.
 *
 * Unregistering publishes `true` before discarding a registration. This leaves any externally held
 * [StateFlow] in a terminal settled state and prevents later callbacks from modifying.
 *
 * @param reserveNextRequestID Atomically reserves the next ID from the owning worker's shared,
 *    monotonically increasing request sequence.
 */
internal class StateMachineSettlingStore(
    private val reserveNextRequestID: () -> Long
) {
    /**
     * Durable settled state for one registered state machine.
     *
     * [requestIDBoundary] is the request ID reserved when the state machine was registered or most
     * recently unsettled. A settled callback is current only when its request ID is greater than
     * this boundary.
     *
     * @param requestIDBoundary The request ID before which settled callbacks are stale.
     */
    private class Slot(var requestIDBoundary: Long) {
        val mutableSettled = MutableStateFlow(false)
        val settled = mutableSettled.asStateFlow()
    }

    /**
     * Serializes registration, generation changes, and settled callbacks.
     *
     * Polling delivers JNI callbacks on the polling caller's thread, while [unsettle] and resource
     * teardown do not enforce that same caller. Keeping lookup, boundary comparison, and state
     * publication in one critical section prevents an older callback from publishing `true`
     * after a newer [unsettle] call publishes `false`. The same lock lets membership in [slots]
     * fully define whether a state machine is registered, without a separate tombstone flag.
     */
    private val lock = Any()

    private val slots = mutableMapOf<StateMachineHandle, Slot>()

    private val _acceptedSettlements = MutableSharedFlow<StateMachineHandle>(
        replay = 0,
        extraBufferCapacity = CommandQueue.MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emits when the canonical state for a registered state machine changes to settled.
     *
     * This event stream exists only to back the deprecated [CommandQueue.settledFlow]
     * compatibility shim. New internal consumers must observe the state-machine-specific [settled]
     * state instead.
     */
    val acceptedSettlements: SharedFlow<StateMachineHandle> = _acceptedSettlements

    /**
     * Registers a newly created state machine for durable settled-state tracking.
     *
     * Its initial request ID boundary ensures only advances issued after registration can mark it
     * settled.
     *
     * @param stateMachineHandle The newly created state machine.
     * @throws IllegalStateException If the handle is already registered.
     */
    fun register(stateMachineHandle: StateMachineHandle) {
        synchronized(lock) {
            check(stateMachineHandle !in slots) {
                "Settled state is already registered for $stateMachineHandle"
            }
            slots[stateMachineHandle] = Slot(reserveNextRequestID())
        }
    }

    /**
     * Unregisters a deleted state machine and discards its durable settled state.
     *
     * Before removal, this publishes `true` as the terminal value for any observer that retains
     * the state flow. Settled callbacks already in flight are ignored once this returns because
     * callbacks consult the same locked registry.
     *
     * @param stateMachineHandle The state machine being deleted.
     */
    fun unregister(stateMachineHandle: StateMachineHandle) {
        synchronized(lock) {
            slots[stateMachineHandle]?.mutableSettled?.value = true
            slots.remove(stateMachineHandle)
        }
    }

    /**
     * Unregisters every state machine when the owning worker is disposed.
     *
     * Each retained state flow receives the same terminal `true` value as [unregister].
     */
    fun clear() {
        synchronized(lock) {
            slots.values.forEach { slot ->
                slot.mutableSettled.value = true
            }
            slots.clear()
        }
    }

    /**
     * Returns the durable settled state for a registered state machine.
     *
     * @param stateMachineHandle The state machine whose state should be observed.
     * @return A flow containing the latest accepted settled state.
     * @throws IllegalStateException If the handle is not registered.
     */
    fun settled(stateMachineHandle: StateMachineHandle): StateFlow<Boolean> =
        synchronized(lock) {
            requireSlot(stateMachineHandle).settled
        }

    /**
     * Begins a new unsettled generation for a state machine.
     *
     * Reserving and storing a new request ID boundary makes callbacks from earlier advances stale.
     * Updating that boundary and the observable state in the same critical section prevents one of
     * those callbacks from publishing `true` after this method publishes `false`.
     *
     * @param stateMachineHandle The state machine to mark unsettled.
     * @throws IllegalStateException If the handle is not registered.
     */
    fun unsettle(stateMachineHandle: StateMachineHandle) {
        synchronized(lock) {
            val slot = requireSlot(stateMachineHandle)
            slot.requestIDBoundary = reserveNextRequestID()
            slot.mutableSettled.value = false
        }
    }

    /**
     * Applies a native settled callback when it belongs to the current generation.
     *
     * Callbacks for deleted or otherwise unknown state machines are ignored because native work
     * already in flight may complete after the corresponding Kotlin resource has closed.
     *
     * @param requestID The worker request that produced the settled callback.
     * @param stateMachineHandle The state machine reported by native code.
     */
    fun settle(requestID: Long, stateMachineHandle: StateMachineHandle) {
        synchronized(lock) {
            val slot = slots[stateMachineHandle] ?: return
            if (requestID > slot.requestIDBoundary && !slot.mutableSettled.value) {
                slot.mutableSettled.value = true
                // Publish from the same critical section as the canonical state change so a newer
                // unsettled boundary cannot be established between acceptance and publication.
                _acceptedSettlements.tryEmit(stateMachineHandle)
            }
        }
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
            "No settled state is registered for $stateMachineHandle"
        }
}
