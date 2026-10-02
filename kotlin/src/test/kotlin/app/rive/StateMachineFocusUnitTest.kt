package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.StateMachineHandle
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Creates a state machine bound to a mocked worker whose settled state is already stubbed.
 *
 * @param worker The mocked worker that should own the state machine.
 * @param stateMachineHandle The handle the state machine should wrap.
 * @return The state machine under test.
 */
private fun focusTestStateMachine(
    worker: CommandQueue,
    stateMachineHandle: StateMachineHandle,
): StateMachine {
    every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(true)
    every { worker.focusState(stateMachineHandle) } returns MutableStateFlow(RiveFocusState())
    every { worker.hasFocusNodes(stateMachineHandle) } returns MutableStateFlow(false)
    return StateMachine(
        stateMachineHandle = stateMachineHandle,
        riveWorker = worker,
        artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
        name = null,
    )
}

class StateMachineFocusUnitTest : FunSpec({
    val fixture = installCommandQueueTestFixture()

    test("Focus next delegates and unsettles in order") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.focusNext(stateMachineHandle) } just runs
        every { worker.unsettleStateMachine(stateMachineHandle) } just runs
        val stateMachine = focusTestStateMachine(worker, stateMachineHandle)

        stateMachine.focusNext()

        verifyOrder {
            worker.focusNext(stateMachineHandle)
            worker.unsettleStateMachine(stateMachineHandle)
        }
    }

    test("Focus previous delegates and unsettles in order") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.focusPrevious(stateMachineHandle) } just runs
        every { worker.unsettleStateMachine(stateMachineHandle) } just runs
        val stateMachine = focusTestStateMachine(worker, stateMachineHandle)

        stateMachine.focusPrevious()

        verifyOrder {
            worker.focusPrevious(stateMachineHandle)
            worker.unsettleStateMachine(stateMachineHandle)
        }
    }

    test("Clear focus delegates and unsettles in order") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.clearFocus(stateMachineHandle) } just runs
        every { worker.unsettleStateMachine(stateMachineHandle) } just runs
        val stateMachine = focusTestStateMachine(worker, stateMachineHandle)

        stateMachine.clearFocus()

        verifyOrder {
            worker.clearFocus(stateMachineHandle)
            worker.unsettleStateMachine(stateMachineHandle)
        }
    }

    test("Request focus state delegates without unsettling") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.requestFocusState(stateMachineHandle) } just runs
        val stateMachine = focusTestStateMachine(worker, stateMachineHandle)

        stateMachine.requestFocusState()

        // Polling runs every frame. Unsettling here would keep the frame loop awake forever.
        verify(exactly = 1) { worker.requestFocusState(stateMachineHandle) }
        verify(exactly = 0) { worker.unsettleStateMachine(any()) }
    }

    test("Request has focus nodes delegates without unsettling") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.requestHasFocusNodes(stateMachineHandle) } just runs
        val stateMachine = focusTestStateMachine(worker, stateMachineHandle)

        stateMachine.requestHasFocusNodes()

        verify(exactly = 1) { worker.requestHasFocusNodes(stateMachineHandle) }
        verify(exactly = 0) { worker.unsettleStateMachine(any()) }
    }

    test("Poll focus delegates without unsettling") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.pollFocus(stateMachineHandle) } just runs
        val stateMachine = focusTestStateMachine(worker, stateMachineHandle)

        stateMachine.pollFocus()

        verify(exactly = 1) { worker.pollFocus(stateMachineHandle) }
        verify(exactly = 0) { worker.unsettleStateMachine(stateMachineHandle) }
    }

    test("Focus flows keep reporting their terminal value after the worker is disposed") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val pollRequestID = slot<Long>()
        every {
            fixture.commandQueueBridgeMock.cppRequestFocusState(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                capture(pollRequestID)
            )
        } just runs
        val stateMachine = StateMachine(
            stateMachineHandle = stateMachineHandle,
            riveWorker = commandQueue,
            artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
            name = null,
        )
        // Start from non-default values, so the terminal values cannot pass by never changing.
        commandQueue.requestFocusState(stateMachineHandle)
        commandQueue.onFocusStateReceived(
            requestID = pollRequestID.captured,
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = true,
        )
        commandQueue.onHasFocusNodesReceived(stateMachineHandle, hasFocusNodes = true)
        stateMachine.focusState.value shouldBe RiveFocusState(true, true)
        stateMachine.hasFocusNodes.value shouldBe true

        commandQueue.release("Test owner")
        commandQueue.awaitShutdown(5_000) shouldBe true

        stateMachine.focusState.value shouldBe RiveFocusState()
        stateMachine.hasFocusNodes.value shouldBe false

        // Answers still in flight at disposal must not revive the retained flows.
        commandQueue.onFocusStateReceived(
            requestID = Long.MAX_VALUE,
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = true,
        )
        commandQueue.onHasFocusNodesReceived(stateMachineHandle, hasFocusNodes = true)

        stateMachine.focusState.value shouldBe RiveFocusState()
        stateMachine.hasFocusNodes.value shouldBe false
    }
})
