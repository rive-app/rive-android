package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.StateMachineHandle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.time.Duration.Companion.ZERO

private const val STATE_MACHINE_TEST_FILE_HANDLE = 789L

class StateMachineUnitTest : FunSpec({
    val fixture = installCommandQueueTestFixture()
    val renderContextMock = fixture.renderContextMock
    val commandQueueBridgeMock = fixture.commandQueueBridgeMock

    test("Compatibility helpers reject another worker or artboard") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val foreignWorker = mockk<CommandQueue>(relaxed = true)
        val artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM)
        val stateMachine = StateMachine(
            StateMachineHandle(HANDLE_NUM),
            worker,
            artboardHandle,
            "Test State Machine",
        )
        val owningArtboard = Artboard(
            artboardHandle,
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
            "Owning Artboard",
        )

        stateMachine.requireOwnedBy(worker)
        stateMachine.requireFromArtboard(owningArtboard)

        shouldThrow<RiveIncompatibleResourceException> {
            stateMachine.requireOwnedBy(foreignWorker)
        }.message shouldContain HANDLE_NUM.toString()
        shouldThrow<RiveIncompatibleResourceException> {
            stateMachine.requireFromArtboard(
                Artboard(
                    ArtboardHandle(ARTBOARD_HANDLE_NUM + 1),
                    worker,
                    FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
                    "Sibling Artboard",
                )
            )
        }.message shouldContain HANDLE_NUM.toString()
        shouldThrow<RiveIncompatibleResourceException> {
            stateMachine.requireFromArtboard(
                Artboard(
                    artboardHandle,
                    foreignWorker,
                    FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
                    "Foreign Artboard",
                )
            )
        }.message shouldContain HANDLE_NUM.toString()
    }

    test("Factory rejects a closed artboard before creating a state machine") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val artboard = Artboard(
            ArtboardHandle(ARTBOARD_HANDLE_NUM),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
            "Closed Artboard",
        ).also { it.close() }

        shouldThrow<RiveResourceClosedException> {
            StateMachine.fromArtboard(artboard)
        }.message shouldContain ARTBOARD_HANDLE_NUM.toString()
        shouldThrow<RiveResourceClosedException> {
            StateMachine.fromArtboard(artboard, "Named State Machine")
        }.message shouldContain ARTBOARD_HANDLE_NUM.toString()

        verify(exactly = 0) {
            worker.createDefaultStateMachine(any())
        }
        verify(exactly = 0) {
            worker.createStateMachineByName(any(), any())
        }
    }

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

        val exception = shouldThrow<RiveResourceClosedException> {
            stateMachine.advance(ZERO)
        }
        exception.message shouldContain HANDLE_NUM.toString()
        verify(exactly = 0) {
            commandQueueBridgeMock.cppAdvanceStateMachine(any(), any(), any(), any())
        }
    }
})
