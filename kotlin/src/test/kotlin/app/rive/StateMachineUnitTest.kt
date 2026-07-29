package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlin.time.Duration.Companion.ZERO

class StateMachineUnitTest : FunSpec({
    val fixture = installCommandQueueTestFixture()
    val renderContextMock = fixture.renderContextMock
    val commandQueueBridgeMock = fixture.commandQueueBridgeMock

    test("Advance throws after close without queuing native work") {
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
        val stateMachine = StateMachine(
            stateMachineHandle = stateMachineHandle,
            riveWorker = commandQueue,
            artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
            name = null
        )

        stateMachine.close()

        shouldThrow<IllegalStateException> {
            stateMachine.advance(ZERO)
        }
        verify(exactly = 0) {
            commandQueueBridgeMock.cppAdvanceStateMachine(any(), any(), any(), any())
        }
    }
})
