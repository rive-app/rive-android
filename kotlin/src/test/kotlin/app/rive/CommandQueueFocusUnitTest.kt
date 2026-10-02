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
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

/**
 * Stubs both traversal commands on [bridge] to be accepted without effect.
 *
 * @param bridge The mocked bridge to stub.
 */
private fun stubTraversals(bridge: CommandQueueBridge) {
    every { bridge.cppFocusNext(COMMAND_QUEUE_ADDR, HANDLE_NUM, any()) } just runs
    every { bridge.cppFocusPrevious(COMMAND_QUEUE_ADDR, HANDLE_NUM, any()) } just runs
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

    test("Move focus submits the command for its direction, then polls") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)
        stubTraversals(fixture.commandQueueBridgeMock)

        for (direction in RiveFocusDirection.entries) {
            coroutineScope {
                val move = async(start = CoroutineStart.UNDISPATCHED) {
                    commandQueue.moveFocus(stateMachineHandle, direction)
                }

                // The command must precede its poll, or the answer would describe the old state.
                verifyOrder {
                    when (direction) {
                        RiveFocusDirection.Next ->
                            fixture.commandQueueBridgeMock
                                .cppFocusNext(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())

                        RiveFocusDirection.Previous ->
                            fixture.commandQueueBridgeMock
                                .cppFocusPrevious(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
                    }
                    fixture.commandQueueBridgeMock.cppRequestFocusState(
                        COMMAND_QUEUE_ADDR,
                        HANDLE_NUM,
                        pollRequestIDs.last(),
                    )
                }
                move.cancel()
            }
        }
    }

    test("Move focus resumes with the answer to its own follow-up poll") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)
        stubTraversals(fixture.commandQueueBridgeMock)

        coroutineScope {
            val move = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Next)
            }
            commandQueue.onFocusStateReceived(
                requestID = pollRequestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                hasFocus = true,
                expectsKeyboardInput = true,
            )

            move.await() shouldBe RiveFocusState.Focused(expectsKeyboardInput = true)
        }
        commandQueue.focusState(stateMachineHandle).value shouldBe
            RiveFocusState.Focused(expectsKeyboardInput = true)
    }

    test("Move focus ignores an answer to a poll requested before it") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)
        stubTraversals(fixture.commandQueueBridgeMock)
        commandQueue.requestFocusState(stateMachineHandle)
        val staleRequestID = pollRequestIDs.single()

        coroutineScope {
            val move = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Next)
            }
            commandQueue.onFocusStateReceived(
                requestID = staleRequestID,
                stateMachineHandle = stateMachineHandle,
                hasFocus = true,
                expectsKeyboardInput = false,
            )

            move.isCompleted shouldBe false
            commandQueue.focusState(stateMachineHandle).value shouldBe RiveFocusState.Unfocused

            commandQueue.onFocusStateReceived(
                requestID = pollRequestIDs.last(),
                stateMachineHandle = stateMachineHandle,
                hasFocus = true,
                expectsKeyboardInput = false,
            )
            move.await() shouldBe RiveFocusState.Focused(expectsKeyboardInput = false)
        }
    }

    test("Move focus rejects an answer to a poll submitted during it") {
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

        coroutineScope {
            val move = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Next)
            }

            // The server holds focus no answer has reported yet, so a stale answer would show.
            deliverInOrder(
                commandQueue,
                stateMachineHandle,
                submissions,
                initiallyFocused = true
            ) shouldBe
                listOf(RiveFocusState.Unfocused, RiveFocusState.Unfocused)
            move.await() shouldBe RiveFocusState.Unfocused
        }
    }

    test("Disposing the worker cancels a move awaiting its answer") {
        // No callback can arrive after disposal, so the move must not wait forever.
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        captureFocusStatePolls(fixture.commandQueueBridgeMock)
        stubTraversals(fixture.commandQueueBridgeMock)

        coroutineScope {
            val move = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Next)
            }

            commandQueue.release("Test owner")
            commandQueue.awaitShutdown(5_000) shouldBe true

            move.join()
            move.isCancelled shouldBe true
        }
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
        focusState.value shouldBe RiveFocusState.Focused(expectsKeyboardInput = false)

        commandQueue.clearFocus(stateMachineHandle)

        pollRequestIDs shouldHaveSize 2
        commandQueue.onFocusStateReceived(
            requestID = pollRequestIDs.last(),
            stateMachineHandle = stateMachineHandle,
            hasFocus = false,
            expectsKeyboardInput = false,
        )
        focusState.value shouldBe RiveFocusState.Unfocused
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
            listOf(RiveFocusState.Unfocused, RiveFocusState.Unfocused)
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

    // The bit layout the JNI layer packs a FocusTraversalResult into. Spelled out as literals so
    // these tests pin the wire format rather than restating whatever production code chose.
})
