package app.rive.core

import app.rive.RiveFocusState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicLong

private const val STATE_MACHINE_HANDLE = 123L
private const val SECOND_STATE_MACHINE_HANDLE = 456L

private val stateMachineHandle = StateMachineHandle(STATE_MACHINE_HANDLE)
private val secondStateMachineHandle = StateMachineHandle(SECOND_STATE_MACHINE_HANDLE)

class FocusStateStoreTest : FunSpec({
    test("A registered state machine starts unfocused and accepts a newer callback") {
        val nextRequestID = AtomicLong()
        val store = FocusStateStore(nextRequestID::getAndIncrement)

        store.register(stateMachineHandle)
        val focusState = store.focusState(stateMachineHandle)
        focusState.value shouldBe RiveFocusState()

        store.applyFocusState(
            requestID = nextRequestID.getAndIncrement(),
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = true,
        )

        focusState.value shouldBe RiveFocusState(hasFocus = true, expectsKeyboardInput = true)
    }

    test("A focus-state callback at or before the registration boundary is rejected") {
        val nextRequestID = AtomicLong()
        val store = FocusStateStore(nextRequestID::getAndIncrement)

        // Registration reserves request 0 as the initial boundary.
        store.register(stateMachineHandle)
        val focusState = store.focusState(stateMachineHandle)

        store.applyFocusState(
            requestID = 0L,
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = false,
        )

        focusState.value shouldBe RiveFocusState()
    }

    test("Invalidating rejects callbacks at or before the new request ID boundary") {
        val nextRequestID = AtomicLong()
        val store = FocusStateStore(nextRequestID::getAndIncrement)
        store.register(stateMachineHandle)
        val focusState = store.focusState(stateMachineHandle)
        val pollRequestID = nextRequestID.getAndIncrement()

        // A focus-changing command reserves a new boundary while the poll above is in flight.
        store.invalidate(stateMachineHandle)
        store.applyFocusState(
            requestID = pollRequestID,
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = false,
        )

        focusState.value shouldBe RiveFocusState()

        store.applyFocusState(
            requestID = nextRequestID.getAndIncrement(),
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = false,
        )

        focusState.value shouldBe RiveFocusState(hasFocus = true)
    }

    test("A registered state machine reports no focus nodes until a callback says otherwise") {
        val nextRequestID = AtomicLong()
        val store = FocusStateStore(nextRequestID::getAndIncrement)

        store.register(stateMachineHandle)
        val hasFocusNodes = store.hasFocusNodes(stateMachineHandle)

        hasFocusNodes.value shouldBe false

        store.applyHasFocusNodes(stateMachineHandle, hasFocusNodes = true)

        hasFocusNodes.value shouldBe true
    }

    test("A focus-changing command does not invalidate has-focus-nodes state") {
        val nextRequestID = AtomicLong()
        val store = FocusStateStore(nextRequestID::getAndIncrement)
        store.register(stateMachineHandle)
        store.applyHasFocusNodes(stateMachineHandle, hasFocusNodes = true)

        store.invalidate(stateMachineHandle)

        store.hasFocusNodes(stateMachineHandle).value shouldBe true
    }

    test("Unregistering publishes an unfocused terminal state and ignores later callbacks") {
        val nextRequestID = AtomicLong()
        val store = FocusStateStore(nextRequestID::getAndIncrement)
        store.register(stateMachineHandle)
        val focusState = store.focusState(stateMachineHandle)
        val hasFocusNodes = store.hasFocusNodes(stateMachineHandle)
        store.applyFocusState(
            requestID = nextRequestID.getAndIncrement(),
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = true,
        )
        store.applyHasFocusNodes(stateMachineHandle, hasFocusNodes = true)

        store.unregister(stateMachineHandle)

        // A deleted state machine cannot hold focus, so observers retaining the flow see that.
        focusState.value shouldBe RiveFocusState()
        hasFocusNodes.value shouldBe false

        store.applyFocusState(
            requestID = Long.MAX_VALUE,
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = true,
        )

        focusState.value shouldBe RiveFocusState()
    }

    test("Clearing publishes the terminal state for every registered state machine") {
        val nextRequestID = AtomicLong()
        val store = FocusStateStore(nextRequestID::getAndIncrement)
        store.register(stateMachineHandle)
        store.register(secondStateMachineHandle)
        store.applyHasFocusNodes(stateMachineHandle, hasFocusNodes = true)
        store.applyHasFocusNodes(secondStateMachineHandle, hasFocusNodes = true)
        val firstHasFocusNodes = store.hasFocusNodes(stateMachineHandle)
        val secondHasFocusNodes = store.hasFocusNodes(secondStateMachineHandle)

        store.clear()

        firstHasFocusNodes.value shouldBe false
        secondHasFocusNodes.value shouldBe false
    }

    test("Invalidating an unregistered state machine is ignored") {
        val nextRequestID = AtomicLong()
        val store = FocusStateStore(nextRequestID::getAndIncrement)

        // A focus command can reach the queue while the state machine is being deleted, so this
        // must not throw the way reading a flow for an unregistered handle does.
        store.invalidate(stateMachineHandle)
    }
})
