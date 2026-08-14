@file:Suppress("DEPRECATION")

package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.ZERO

class StateMachineSettlingUnitTest : FunSpec({
    val fixture = installCommandQueueTestFixture()
    val renderContextMock = fixture.renderContextMock
    val commandQueueBridgeMock = fixture.commandQueueBridgeMock

    test("Deprecated worker settled flow delegates to accepted canonical transitions") {
        coroutineScope {
            val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
            every {
                commandQueueBridgeMock.cppCreateDefaultStateMachine(
                    COMMAND_QUEUE_ADDR,
                    any(),
                    ARTBOARD_HANDLE_NUM
                )
            } returns HANDLE_NUM
            every {
                commandQueueBridgeMock.cppDeleteStateMachine(
                    COMMAND_QUEUE_ADDR,
                    any(),
                    HANDLE_NUM
                )
            } just runs
            val stateMachineHandle =
                commandQueue.createDefaultStateMachine(ArtboardHandle(ARTBOARD_HANDLE_NUM))
            val settledEvent = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.settledFlow.first()
            }

            commandQueue.onStateMachineSettled(Long.MAX_VALUE, stateMachineHandle)

            withTimeout(1_000L) {
                settledEvent.await() shouldBe stateMachineHandle
            }
            commandQueue.deleteStateMachine(stateMachineHandle)
        }
    }

    test("Settled state rejects callbacks from before the latest worker boundary") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val advanceRequestIDs = mutableListOf<Long>()
        every {
            commandQueueBridgeMock.cppCreateDefaultStateMachine(
                COMMAND_QUEUE_ADDR,
                any(),
                ARTBOARD_HANDLE_NUM
            )
        } returns HANDLE_NUM
        every {
            commandQueueBridgeMock.cppAdvanceStateMachine(
                COMMAND_QUEUE_ADDR,
                capture(advanceRequestIDs),
                HANDLE_NUM,
                0L
            )
        } just runs
        every {
            commandQueueBridgeMock.cppDeleteStateMachine(
                COMMAND_QUEUE_ADDR,
                any(),
                HANDLE_NUM
            )
        } just runs
        val stateMachineHandle =
            commandQueue.createDefaultStateMachine(ArtboardHandle(ARTBOARD_HANDLE_NUM))
        val settled = commandQueue.stateMachineSettled(stateMachineHandle)

        settled.value shouldBe false
        commandQueue.advanceStateMachine(stateMachineHandle, ZERO)
        commandQueue.onStateMachineSettled(advanceRequestIDs[0], stateMachineHandle)
        settled.value shouldBe true

        val previousGenerationRequestID = advanceRequestIDs[0]
        commandQueue.unsettleStateMachine(stateMachineHandle)
        settled.value shouldBe false
        commandQueue.onStateMachineSettled(previousGenerationRequestID, stateMachineHandle)
        settled.value shouldBe false

        advanceRequestIDs shouldHaveSize 1
        commandQueue.deleteStateMachine(stateMachineHandle)
    }

    test("Deleting a state machine unregisters its settled state and ignores late callbacks") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        every {
            commandQueueBridgeMock.cppCreateDefaultStateMachine(
                COMMAND_QUEUE_ADDR,
                any(),
                ARTBOARD_HANDLE_NUM
            )
        } returns HANDLE_NUM
        every {
            commandQueueBridgeMock.cppDeleteStateMachine(
                COMMAND_QUEUE_ADDR,
                any(),
                HANDLE_NUM
            )
        } just runs
        val stateMachineHandle =
            commandQueue.createDefaultStateMachine(ArtboardHandle(ARTBOARD_HANDLE_NUM))
        val settled = commandQueue.stateMachineSettled(stateMachineHandle)

        commandQueue.deleteStateMachine(stateMachineHandle)
        commandQueue.onStateMachineSettled(Long.MAX_VALUE, stateMachineHandle)
        settled.value shouldBe true
        shouldThrow<IllegalStateException> {
            commandQueue.stateMachineSettled(stateMachineHandle)
        }
    }

    test("State machine exposes shared settled state across callbacks, unsettle, and close") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val advanceRequestIDs = mutableListOf<Long>()
        every {
            commandQueueBridgeMock.cppCreateDefaultStateMachine(
                COMMAND_QUEUE_ADDR,
                any(),
                ARTBOARD_HANDLE_NUM
            )
        } returns HANDLE_NUM
        every {
            commandQueueBridgeMock.cppAdvanceStateMachine(
                COMMAND_QUEUE_ADDR,
                capture(advanceRequestIDs),
                HANDLE_NUM,
                0L
            )
        } just runs
        every {
            commandQueueBridgeMock.cppDeleteStateMachine(
                COMMAND_QUEUE_ADDR,
                any(),
                HANDLE_NUM
            )
        } just runs
        val stateMachineHandle =
            commandQueue.createDefaultStateMachine(ArtboardHandle(ARTBOARD_HANDLE_NUM))
        val stateMachine = StateMachine(
            stateMachineHandle = stateMachineHandle,
            riveWorker = commandQueue,
            artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
            name = null
        )

        stateMachine.settled.value shouldBe false
        stateMachine.advance(ZERO)
        commandQueue.onStateMachineSettled(advanceRequestIDs.last(), stateMachineHandle)
        stateMachine.settled.value shouldBe true

        val previousGenerationRequestID = advanceRequestIDs.last()
        stateMachine.unsettle()
        stateMachine.settled.value shouldBe false
        commandQueue.onStateMachineSettled(previousGenerationRequestID, stateMachineHandle)
        stateMachine.settled.value shouldBe false

        stateMachine.advance(ZERO)
        commandQueue.onStateMachineSettled(advanceRequestIDs.last(), stateMachineHandle)
        stateMachine.settled.value shouldBe true

        stateMachine.unsettle()
        stateMachine.settled.value shouldBe false
        stateMachine.close()
        stateMachine.settled.value shouldBe true

        val exception = shouldThrow<RiveResourceClosedException> {
            stateMachine.unsettle()
        }
        exception.message shouldContain HANDLE_NUM.toString()
        stateMachine.settled.value shouldBe true
    }
})
