package app.rive

import android.view.KeyEvent
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
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

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
 * Accepts every focus move on [bridge] without effect and records its request ID.
 *
 * @param bridge The mocked bridge to stub.
 * @return A live list of the request IDs of submitted moves.
 */
private fun stubMoves(bridge: CommandQueueBridge): List<Long> {
    val requestIDs = mutableListOf<Long>()
    every {
        bridge.cppMoveFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM, any(), capture(requestIDs))
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

    test("Move focus submits one command carrying its direction") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        stubMoves(fixture.commandQueueBridgeMock)

        for (direction in RiveFocusDirection.entries) {
            coroutineScope {
                val move = async(start = CoroutineStart.UNDISPATCHED) {
                    commandQueue.moveFocus(stateMachineHandle, direction)
                }

                verify(exactly = 1) {
                    fixture.commandQueueBridgeMock.cppMoveFocus(
                        COMMAND_QUEUE_ADDR,
                        HANDLE_NUM,
                        direction.nativeMapping,
                        any(),
                    )
                }
                move.cancel()
            }
        }
    }

    test("Move focus resumes with its own outcome and publishes the focus state") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val moveRequestIDs = stubMoves(fixture.commandQueueBridgeMock)

        coroutineScope {
            val move = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Down)
            }
            commandQueue.onFocusMoved(
                requestID = moveRequestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                moved = false,
                hasFocus = true,
                expectsKeyboardInput = true,
            )

            move.await() shouldBe RiveFocusMoveResult(
                moved = false,
                focusState = RiveFocusState.Focused(expectsKeyboardInput = true),
            )
        }
        commandQueue.focusState(stateMachineHandle).value shouldBe
            RiveFocusState.Focused(expectsKeyboardInput = true)
    }

    test("Move focus discards the answer to a poll requested before it") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)
        stubMoves(fixture.commandQueueBridgeMock)
        commandQueue.requestFocusState(stateMachineHandle)

        coroutineScope {
            val move = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Next)
            }
            commandQueue.onFocusStateReceived(
                requestID = pollRequestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                hasFocus = true,
                expectsKeyboardInput = false,
            )

            commandQueue.focusState(stateMachineHandle).value shouldBe RiveFocusState.Unfocused
            move.cancel()
        }
    }

    test("Move focus rejects an answer to a poll submitted during it") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val submissions = recordFocusStatePolls(fixture.commandQueueBridgeMock)
        val moveRequestIDs = mutableListOf<Long>()
        every {
            fixture.commandQueueBridgeMock.cppMoveFocus(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                any(),
                capture(moveRequestIDs)
            )
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
            ) shouldBe listOf(RiveFocusState.Unfocused)
            commandQueue.onFocusMoved(
                requestID = moveRequestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                moved = false,
                hasFocus = false,
                expectsKeyboardInput = false,
            )
            move.await().focusState shouldBe RiveFocusState.Unfocused
        }
    }

    test("A clear another thread submits while a move is being submitted stays current") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)
        every {
            fixture.commandQueueBridgeMock.cppClearFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } just runs
        val moveRequestIDs = mutableListOf<Long>()
        lateinit var clearing: Thread
        every {
            fixture.commandQueueBridgeMock.cppMoveFocus(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                any(),
                capture(moveRequestIDs),
            )
        } answers {
            // Another thread clears focus while the move is being submitted. Wait until it either
            // finishes or waits for the move's submission to complete.
            clearing = thread { commandQueue.clearFocus(stateMachineHandle) }
            while (clearing.isAlive && clearing.state != Thread.State.BLOCKED) {
                Thread.sleep(1)
            }
        }

        coroutineScope {
            val move = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Next)
            }
            clearing.join()

            // The command server ran the move, then the clear, then the clear's poll.
            commandQueue.onFocusMoved(
                requestID = moveRequestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                moved = true,
                hasFocus = true,
                expectsKeyboardInput = false,
            )
            commandQueue.onFocusStateReceived(
                requestID = pollRequestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                hasFocus = false,
                expectsKeyboardInput = false,
            )
            move.await()
        }
        commandQueue.focusState(stateMachineHandle).value shouldBe RiveFocusState.Unfocused
    }

    test("A move's outcome doesn't overwrite a later focus-changing command") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val moveRequestIDs = stubMoves(fixture.commandQueueBridgeMock)
        captureFocusStatePolls(fixture.commandQueueBridgeMock)
        every {
            fixture.commandQueueBridgeMock.cppClearFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM, any())
        } just runs

        coroutineScope {
            val move = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Next)
            }
            commandQueue.clearFocus(stateMachineHandle)
            commandQueue.onFocusMoved(
                requestID = moveRequestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                moved = true,
                hasFocus = true,
                expectsKeyboardInput = false,
            )

            move.await().moved shouldBe true
        }
        // The clear was submitted after the move, so the move's outcome must not show as current.
        commandQueue.focusState(stateMachineHandle).value shouldBe RiveFocusState.Unfocused
    }

    test("A poll answered after a move's outcome keeps its newer state") {
        runTest {
            val commandQueue =
                CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
            val stateMachineHandle = fixture.registerStateMachine(commandQueue)
            val moveRequestIDs = stubMoves(fixture.commandQueueBridgeMock)
            val pollRequestIDs = captureFocusStatePolls(fixture.commandQueueBridgeMock)
            // The test dispatcher resumes the move only when the test yields, as a caller on a
            // dispatching main dispatcher does.
            val move = async { commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Next) }
            runCurrent()
            commandQueue.requestFocusState(stateMachineHandle)

            commandQueue.onFocusMoved(
                requestID = moveRequestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                moved = true,
                hasFocus = true,
                expectsKeyboardInput = false,
            )
            // The advance after the move cleared focus, and the poll after it reports that.
            commandQueue.onFocusStateReceived(
                requestID = pollRequestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                hasFocus = false,
                expectsKeyboardInput = false,
            )
            move.await()

            commandQueue.focusState(stateMachineHandle).value shouldBe RiveFocusState.Unfocused
        }
    }

    test("A move publishes its outcome after its caller stops waiting") {
        runTest {
            val commandQueue =
                CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
            val stateMachineHandle = fixture.registerStateMachine(commandQueue)
            val moveRequestIDs = stubMoves(fixture.commandQueueBridgeMock)
            val move = async { commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Next) }
            runCurrent()
            move.cancel()
            runCurrent()

            commandQueue.onFocusMoved(
                requestID = moveRequestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                moved = true,
                hasFocus = true,
                expectsKeyboardInput = true,
            )

            commandQueue.focusState(stateMachineHandle).value shouldBe
                RiveFocusState.Focused(expectsKeyboardInput = true)
        }
    }

    test("Disposing the worker cancels a move awaiting its answer") {
        // The command server stops answering at disposal, so the move must not wait forever.
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val moveRequestIDs = stubMoves(fixture.commandQueueBridgeMock)
        val focusState = commandQueue.focusState(stateMachineHandle)

        coroutineScope {
            val move = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.moveFocus(stateMachineHandle, RiveFocusDirection.Next)
            }

            commandQueue.release("Test owner")
            commandQueue.awaitShutdown(5_000) shouldBe true

            move.join()
            move.isCancelled shouldBe true
        }

        // An answer still in flight at disposal must not revive the retained flow.
        commandQueue.onFocusMoved(
            requestID = moveRequestIDs.single(),
            stateMachineHandle = stateMachineHandle,
            moved = true,
            hasFocus = true,
            expectsKeyboardInput = true,
        )
        focusState.value shouldBe RiveFocusState.Unfocused
    }

    test("Key input forwards the press and resumes with whether it was handled") {
        val commandQueue = CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
        val stateMachineHandle = fixture.registerStateMachine(commandQueue)
        val requestIDs = mutableListOf<Long>()
        every {
            fixture.commandQueueBridgeMock.cppKeyInput(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                257,
                1,
                true,
                false,
                capture(requestIDs)
            )
        } just runs
        val press = RiveKeyPress(
            keyCode = KeyEvent.KEYCODE_ENTER,
            key = 257,
            modifiers = 1,
            isPressed = true,
            isRepeat = false,
            navigation = null,
            isChord = false,
        )

        coroutineScope {
            val handled = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.keyInput(stateMachineHandle, press)
            }
            commandQueue.onKeyInputHandled(
                requestID = requestIDs.single(),
                stateMachineHandle = stateMachineHandle,
                handled = true,
            )

            handled.await() shouldBe true
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
})
