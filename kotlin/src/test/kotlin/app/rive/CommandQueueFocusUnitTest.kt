package app.rive

import app.rive.core.CommandQueue
import app.rive.core.CommandQueueBridge
import app.rive.core.StateMachineHandle
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify

/**
 * Captures the request IDs of every focus-state poll submitted to [bridge].
 *
 * @param bridge The mocked bridge whose poll calls should be recorded.
 * @return A live list that grows as polls are submitted.
 */
private fun captureFocusStatePolls(bridge: CommandQueueBridge): List<Long> {
    val requestIDs = mutableListOf<Long>()
    every {
        bridge.cppRequestFocusState(COMMAND_QUEUE_ADDR, HANDLE_NUM, capture(requestIDs))
    } just runs
    return requestIDs
}

/** A focus submission, in the order the command server receives it. */
private sealed interface FocusSubmission {
    data class Poll(val requestID: Long) : FocusSubmission

    data class Command(val hasFocusAfter: Boolean) : FocusSubmission
}

/**
 * Records focus-state polls on [bridge] in submission order.
 *
 * @param bridge The mocked bridge whose poll calls should be recorded.
 * @return A live list that tests can also append commands to.
 */
private fun recordFocusStatePolls(bridge: CommandQueueBridge): MutableList<FocusSubmission> {
    val submissions = mutableListOf<FocusSubmission>()
    every {
        bridge.cppRequestFocusState(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
    } answers {
        submissions += FocusSubmission.Poll(thirdArg())
    }
    return submissions
}

/**
 * Answers each poll in [submissions] with the focus the server holds when it runs.
 *
 * @param initiallyFocused Whether the server holds focus before the first submission.
 * @return The observable focus state after each answer.
 */
private fun deliverInOrder(
    commandQueue: CommandQueue,
    stateMachineHandle: StateMachineHandle,
    submissions: List<FocusSubmission>,
    initiallyFocused: Boolean,
): List<RiveFocusState> {
    var serverHasFocus = initiallyFocused
    return submissions.mapNotNull { submission ->
        when (submission) {
            is FocusSubmission.Command -> {
                serverHasFocus = submission.hasFocusAfter
                null
            }

            is FocusSubmission.Poll -> {
                commandQueue.onFocusStateReceived(
                    requestID = submission.requestID,
                    stateMachineHandle = stateMachineHandle,
                    hasFocus = serverHasFocus,
                    expectsKeyboardInput = false,
                )
                commandQueue.focusState(stateMachineHandle).value
            }
        }
    }
}

class CommandQueueFocusUnitTest : FunSpec({
    val fixture = installCommandQueueTestFixture()

    test("Focus next invokes native") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)

        every {
            fixture.commandQueueBridgeMock.cppFocusNext(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } just runs
        captureFocusStatePolls(fixture.commandQueueBridgeMock)

        commandQueue.focusNext(stateMachineHandle)

        verify(exactly = 1) {
            fixture.commandQueueBridgeMock.cppFocusNext(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        }
    }

    test("Focus next requests the focus state it produces") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)
        every {
            fixture.commandQueueBridgeMock.cppFocusNext(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } just runs
        val focusState = commandQueue.focusState(stateMachineHandle)

        commandQueue.focusNext(stateMachineHandle)

        pollRequestIDs shouldHaveSize 1
        commandQueue.onFocusStateReceived(
            requestID = pollRequestIDs.single(),
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = false,
        )
        focusState.value shouldBe RiveFocusState(hasFocus = true)
    }

    test("Submitting focus next rejects a focus state answer requested before it") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val pollRequestIDs = mutableListOf<Long>()
        every {
            fixture.commandQueueBridgeMock.cppRequestFocusState(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                capture(pollRequestIDs)
            )
        } just runs
        every {
            fixture.commandQueueBridgeMock.cppFocusNext(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } just runs
        val focusState = commandQueue.focusState(stateMachineHandle)

        commandQueue.requestFocusState(stateMachineHandle)
        commandQueue.focusNext(stateMachineHandle)
        commandQueue.onFocusStateReceived(
            requestID = pollRequestIDs.first(),
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = false,
        )

        focusState.value shouldBe RiveFocusState()

        commandQueue.requestFocusState(stateMachineHandle)
        commandQueue.onFocusStateReceived(
            requestID = pollRequestIDs.last(),
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = false,
        )

        focusState.value shouldBe RiveFocusState(hasFocus = true)
    }

    test("Focus next rejects an answer to a poll submitted during it") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val submissions = recordFocusStatePolls(fixture.commandQueueBridgeMock)
        every {
            fixture.commandQueueBridgeMock.cppFocusNext(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } answers {
            // A poll from another thread reaches the server ahead of the command.
            commandQueue.requestFocusState(stateMachineHandle)
            submissions += FocusSubmission.Command(hasFocusAfter = false)
        }

        commandQueue.focusNext(stateMachineHandle)

        // The server holds focus no answer has reported yet, so a stale answer would show.
        deliverInOrder(
            commandQueue,
            stateMachineHandle,
            submissions,
            initiallyFocused = true
        ) shouldBe
            listOf(RiveFocusState(), RiveFocusState())
    }

    test("Focus previous invokes native") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)

        every {
            fixture.commandQueueBridgeMock.cppFocusPrevious(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } just runs
        captureFocusStatePolls(fixture.commandQueueBridgeMock)

        commandQueue.focusPrevious(stateMachineHandle)

        verify(exactly = 1) {
            fixture.commandQueueBridgeMock.cppFocusPrevious(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        }
    }

    test("Focus previous requests the focus state it produces") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)
        every {
            fixture.commandQueueBridgeMock.cppFocusPrevious(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } just runs
        val focusState = commandQueue.focusState(stateMachineHandle)

        commandQueue.focusPrevious(stateMachineHandle)

        pollRequestIDs shouldHaveSize 1
        commandQueue.onFocusStateReceived(
            requestID = pollRequestIDs.single(),
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = false,
        )
        focusState.value shouldBe RiveFocusState(hasFocus = true)
    }

    test("Clear focus invokes native") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)

        every {
            fixture.commandQueueBridgeMock.cppClearFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } just runs
        captureFocusStatePolls(fixture.commandQueueBridgeMock)

        commandQueue.clearFocus(stateMachineHandle)

        verify(exactly = 1) {
            fixture.commandQueueBridgeMock.cppClearFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        }
    }

    test("Clear focus requests the focus state it produces") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)
        every {
            fixture.commandQueueBridgeMock.cppClearFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } just runs
        val focusState = commandQueue.focusState(stateMachineHandle)
        // Start focused, so dropping focus is distinguishable from the initial value and the
        // assertion cannot pass on an answer that was never applied.
        commandQueue.requestFocusState(stateMachineHandle)
        commandQueue.onFocusStateReceived(
            requestID = pollRequestIDs.single(),
            stateMachineHandle = stateMachineHandle,
            hasFocus = true,
            expectsKeyboardInput = false,
        )
        focusState.value shouldBe RiveFocusState(hasFocus = true)

        commandQueue.clearFocus(stateMachineHandle)

        pollRequestIDs shouldHaveSize 2
        commandQueue.onFocusStateReceived(
            requestID = pollRequestIDs.last(),
            stateMachineHandle = stateMachineHandle,
            hasFocus = false,
            expectsKeyboardInput = false,
        )
        focusState.value shouldBe RiveFocusState(hasFocus = false)
    }

    test("Clear focus rejects an answer to a poll submitted during it") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val submissions = recordFocusStatePolls(fixture.commandQueueBridgeMock)
        every {
            fixture.commandQueueBridgeMock.cppClearFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } answers {
            // A poll from another thread reaches the server ahead of the command.
            commandQueue.requestFocusState(stateMachineHandle)
            submissions += FocusSubmission.Command(hasFocusAfter = false)
        }

        commandQueue.clearFocus(stateMachineHandle)

        // The server holds focus no answer has reported yet, so a stale answer would show.
        deliverInOrder(
            commandQueue,
            stateMachineHandle,
            submissions,
            initiallyFocused = true
        ) shouldBe
            listOf(RiveFocusState(), RiveFocusState())
    }

    test("A has focus nodes callback updates the observable value") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val hasFocusNodes = commandQueue.hasFocusNodes(stateMachineHandle)

        commandQueue.onHasFocusNodesReceived(stateMachineHandle, hasFocusNodes = true)

        hasFocusNodes.value shouldBe true
    }

    test("Poll focus requests focus state before the content check has answered") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        every {
            fixture.commandQueueBridgeMock.cppRequestHasFocusNodes(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                any()
            )
        } just runs
        val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)

        // No has-focus-nodes answer has arrived yet. An advance that adds focusable content and
        // assigns focus can also settle the state machine, ending the frame loop, so this poll is
        // the only chance to ask for the focus state that advance produced.
        commandQueue.pollFocus(stateMachineHandle)

        pollRequestIDs shouldHaveSize 1
    }

    test("Poll focus tolerates a state machine the worker no longer tracks") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        // Never registered: a frame loop can reach this after the state machine is deleted.
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every {
            fixture.commandQueueBridgeMock.cppRequestHasFocusNodes(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                any()
            )
        } just runs
        val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)

        commandQueue.pollFocus(stateMachineHandle)

        verify(exactly = 1) {
            fixture.commandQueueBridgeMock.cppRequestHasFocusNodes(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                any()
            )
        }
        pollRequestIDs shouldHaveSize 1
    }
})
