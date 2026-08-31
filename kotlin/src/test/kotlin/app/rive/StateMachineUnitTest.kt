package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.StateMachineHandle
import app.rive.core.ViewModelInstanceHandle
import app.rive.semantics.SemanticActionType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.ZERO

private const val STATE_MACHINE_TEST_FILE_HANDLE = 789L

@OptIn(ExperimentalRiveGlobalViewModels::class)
class StateMachineUnitTest : FunSpec({
    val fixture = installCommandQueueTestFixture()
    val renderContextMock = fixture.renderContextMock
    val commandQueueBridgeMock = fixture.commandQueueBridgeMock

    test("Factory returns a named state machine") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val artboard = Artboard(
            ArtboardHandle(ARTBOARD_HANDLE_NUM),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
            "Test Artboard",
        )
        val handle = StateMachineHandle(HANDLE_NUM)
        coEvery {
            worker.createStateMachineByNameConfirmed(
                artboard.artboardHandle,
                "Named State Machine",
            )
        } returns handle
        every { worker.stateMachineSettled(handle) } returns MutableStateFlow(false)

        val stateMachine = StateMachine.create(artboard, "Named State Machine")

        stateMachine.stateMachineHandle shouldBe handle
        stateMachine.name shouldBe "Named State Machine"
        coVerify(exactly = 1) {
            worker.createStateMachineByNameConfirmed(
                artboard.artboardHandle,
                "Named State Machine",
            )
        }
    }

    test("Factory propagates creation failure") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val artboard = Artboard(
            ArtboardHandle(ARTBOARD_HANDLE_NUM),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
            "Test Artboard",
        )
        val error = RiveArtboardException("Missing state machine")
        coEvery {
            worker.createDefaultStateMachineConfirmed(artboard.artboardHandle)
        } throws error

        shouldThrow<RiveArtboardException> {
            StateMachine.create(artboard)
        } shouldBe error
    }

    test("Factory propagates cancellation") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val artboard = Artboard(
            ArtboardHandle(ARTBOARD_HANDLE_NUM),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
            "Test Artboard",
        )
        val cancellation = CancellationException("Cancelled state machine creation")
        coEvery {
            worker.createDefaultStateMachineConfirmed(artboard.artboardHandle)
        } throws cancellation

        shouldThrow<CancellationException> {
            StateMachine.create(artboard)
        } shouldBe cancellation
    }

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

    test("View model bindings apply an initial empty configuration only once") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(false)
        val stateMachine = StateMachine(
            stateMachineHandle,
            worker,
            ArtboardHandle(ARTBOARD_HANDLE_NUM),
            "Test State Machine",
        )
        clearMocks(worker, answers = false, recordedCalls = true)

        stateMachine.bindViewModels(null, emptyMap())
        stateMachine.bindViewModels(null, emptyMap())

        verify(exactly = 1) { worker.bind(stateMachineHandle) }
        verify(exactly = 1) { worker.unsettleStateMachine(stateMachineHandle) }
        verify(exactly = 0) { worker.setMainViewModelInstance(any(), any()) }
        verify(exactly = 0) { worker.clearMainViewModelInstance(any()) }
        verify(exactly = 0) { worker.setGlobalViewModelInstance(any(), any(), any()) }
        verify(exactly = 0) { worker.clearGlobalViewModelInstance(any(), any()) }
    }

    test("View model bindings diff the complete configuration") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(false)
        val stateMachine = StateMachine(
            stateMachineHandle,
            worker,
            ArtboardHandle(ARTBOARD_HANDLE_NUM),
            "Test State Machine",
        )
        val mainA = ViewModelInstance(
            ViewModelInstanceHandle(100),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
        )
        val mainB = ViewModelInstance(
            ViewModelInstanceHandle(101),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
        )
        val globalA = ViewModelInstance(
            ViewModelInstanceHandle(200),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
        )
        val globalB = ViewModelInstance(
            ViewModelInstanceHandle(201),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
        )
        val preserved = ViewModelInstance(
            ViewModelInstanceHandle(202),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
        )

        stateMachine.bindViewModels(
            mainA,
            linkedMapOf(
                "Theme" to globalA,
                "Locale" to globalB,
                "Preserved" to preserved,
            ),
        )
        clearMocks(worker, answers = false, recordedCalls = true)

        // Replace main and Theme, remove Locale, add Motion, and retain Preserved unchanged.
        stateMachine.bindViewModels(
            mainB,
            linkedMapOf(
                "Theme" to globalB,
                "Preserved" to preserved,
                "Motion" to globalA,
            ),
        )

        verifyOrder {
            worker.setMainViewModelInstance(stateMachineHandle, mainB.instanceHandle)
            worker.clearGlobalViewModelInstance(stateMachineHandle, "Locale")
            worker.setGlobalViewModelInstance(
                stateMachineHandle,
                "Theme",
                globalB.instanceHandle,
            )
            worker.setGlobalViewModelInstance(
                stateMachineHandle,
                "Motion",
                globalA.instanceHandle,
            )
            worker.bind(stateMachineHandle)
            worker.unsettleStateMachine(stateMachineHandle)
        }
        verify(exactly = 0) {
            worker.setGlobalViewModelInstance(stateMachineHandle, "Preserved", any())
        }
        verify(exactly = 0) {
            worker.clearGlobalViewModelInstance(stateMachineHandle, "Preserved")
        }

        clearMocks(worker, answers = false, recordedCalls = true)

        // Removing the explicit main and globals clears only their former slots.
        stateMachine.bindViewModels(null, mapOf("Preserved" to preserved))

        verifyOrder {
            worker.clearMainViewModelInstance(stateMachineHandle)
            worker.clearGlobalViewModelInstance(stateMachineHandle, "Theme")
            worker.clearGlobalViewModelInstance(stateMachineHandle, "Motion")
            worker.bind(stateMachineHandle)
            worker.unsettleStateMachine(stateMachineHandle)
        }
        verify(exactly = 0) {
            worker.clearGlobalViewModelInstance(stateMachineHandle, "Preserved")
        }
    }

    test("View model bindings validate every instance before dispatch") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val foreignWorker = mockk<CommandQueue>(relaxed = true)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(false)
        val stateMachine = StateMachine(
            stateMachineHandle,
            worker,
            ArtboardHandle(ARTBOARD_HANDLE_NUM),
            "Test State Machine",
        )
        val main = ViewModelInstance(
            ViewModelInstanceHandle(100),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
        )
        val foreignGlobal = ViewModelInstance(
            ViewModelInstanceHandle(200),
            foreignWorker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
        )
        clearMocks(worker, answers = false, recordedCalls = true)

        shouldThrow<RiveIncompatibleResourceException> {
            stateMachine.bindViewModels(main, mapOf("Theme" to foreignGlobal))
        }

        verify(exactly = 0) { worker.setMainViewModelInstance(any(), any()) }
        verify(exactly = 0) { worker.setGlobalViewModelInstance(any(), any(), any()) }
        verify(exactly = 0) { worker.bind(any()) }
        verify(exactly = 0) { worker.unsettleStateMachine(any()) }
    }

    test("View model bindings snapshot a mutable global map") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(false)
        val stateMachine = StateMachine(
            stateMachineHandle,
            worker,
            ArtboardHandle(ARTBOARD_HANDLE_NUM),
            "Test State Machine",
        )
        val first = ViewModelInstance(
            ViewModelInstanceHandle(200),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
        )
        val replacement = ViewModelInstance(
            ViewModelInstanceHandle(201),
            worker,
            FileHandle(STATE_MACHINE_TEST_FILE_HANDLE),
        )
        val globals = mutableMapOf("Theme" to first)
        stateMachine.bindViewModels(null, globals)
        clearMocks(worker, answers = false, recordedCalls = true)

        globals["Theme"] = replacement
        stateMachine.bindViewModels(null, globals)

        verify(exactly = 1) {
            worker.setGlobalViewModelInstance(
                stateMachineHandle,
                "Theme",
                replacement.instanceHandle,
            )
        }
        verify(exactly = 1) { worker.bind(stateMachineHandle) }
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

        stateMachine.closed shouldBe false
        stateMachine.close()
        stateMachine.closed shouldBe true

        val exception = shouldThrow<RiveResourceClosedException> {
            stateMachine.advance(ZERO)
        }
        exception.message shouldContain HANDLE_NUM.toString()
        verify(exactly = 0) {
            commandQueueBridgeMock.cppAdvanceStateMachine(any(), any(), any(), any())
        }
    }

    test("Enable semantics delegates and unsettles in order") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(true)
        every { worker.enableSemantics(stateMachineHandle) } just runs
        every { worker.unsettleStateMachine(stateMachineHandle) } just runs
        val stateMachine = StateMachine(
            stateMachineHandle = stateMachineHandle,
            riveWorker = worker,
            artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
            name = null
        )

        stateMachine.enableSemantics()

        verifyOrder {
            worker.enableSemantics(stateMachineHandle)
            worker.unsettleStateMachine(stateMachineHandle)
        }
    }

    test("Fire semantic action delegates and unsettles in order") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(true)
        every { worker.fireSemanticAction(stateMachineHandle, 17, SemanticActionType.Tap) } just runs
        every { worker.unsettleStateMachine(stateMachineHandle) } just runs
        val stateMachine = StateMachine(
            stateMachineHandle = stateMachineHandle,
            riveWorker = worker,
            artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
            name = null
        )

        stateMachine.fireSemanticAction(17, SemanticActionType.Tap)

        verifyOrder {
            worker.fireSemanticAction(stateMachineHandle, 17, SemanticActionType.Tap)
            worker.unsettleStateMachine(stateMachineHandle)
        }
    }

    test("Request semantic focus delegates and unsettles in order") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(true)
        every { worker.requestSemanticFocus(stateMachineHandle, 42) } just runs
        every { worker.unsettleStateMachine(stateMachineHandle) } just runs
        val stateMachine = StateMachine(
            stateMachineHandle = stateMachineHandle,
            riveWorker = worker,
            artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
            name = null
        )

        stateMachine.requestSemanticFocus(42)

        verifyOrder {
            worker.requestSemanticFocus(stateMachineHandle, 42)
            worker.unsettleStateMachine(stateMachineHandle)
        }
    }

    test("Clear semantic focus delegates and unsettles in order") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(true)
        every { worker.clearSemanticFocus(stateMachineHandle) } just runs
        every { worker.unsettleStateMachine(stateMachineHandle) } just runs
        val stateMachine = StateMachine(
            stateMachineHandle = stateMachineHandle,
            riveWorker = worker,
            artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
            name = null
        )

        stateMachine.clearSemanticFocus()

        verifyOrder {
            worker.clearSemanticFocus(stateMachineHandle)
            worker.unsettleStateMachine(stateMachineHandle)
        }
    }

    test("Lifecycle focus cleanup ignores a closed state machine") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(true)
        val stateMachine = StateMachine(
            stateMachineHandle = stateMachineHandle,
            riveWorker = worker,
            artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
            name = null
        )
        stateMachine.close()

        stateMachine.clearSemanticFocusForLifecycleCleanup()

        verify(exactly = 0) { worker.clearSemanticFocus(any()) }
    }

    test("Lifecycle focus cleanup ignores a disposed worker") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(true)
        every { worker.clearSemanticFocus(stateMachineHandle) } throws
            RiveResourceClosedException("RiveWorker is disposed")
        val stateMachine = StateMachine(
            stateMachineHandle = stateMachineHandle,
            riveWorker = worker,
            artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
            name = null
        )

        stateMachine.clearSemanticFocusForLifecycleCleanup()

        verify(exactly = 0) { worker.unsettleStateMachine(any()) }
    }

    test("Lifecycle focus cleanup preserves invariant failures") {
        val worker = mockk<CommandQueue>()
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        every { worker.stateMachineSettled(stateMachineHandle) } returns MutableStateFlow(true)
        every { worker.clearSemanticFocus(stateMachineHandle) } throws
            IllegalStateException("State machine is not registered")
        val stateMachine = StateMachine(
            stateMachineHandle = stateMachineHandle,
            riveWorker = worker,
            artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM),
            name = null
        )

        shouldThrow<IllegalStateException> {
            stateMachine.clearSemanticFocusForLifecycleCleanup()
        }
    }
})
