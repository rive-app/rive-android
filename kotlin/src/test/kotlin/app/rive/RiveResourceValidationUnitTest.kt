package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.StateMachineHandle
import app.rive.core.ViewModelInstanceHandle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk

private const val RIVE_VALIDATION_FILE_HANDLE = 50L
private const val RIVE_VALIDATION_OTHER_FILE_HANDLE = 51L
private const val RIVE_VALIDATION_ARTBOARD_HANDLE = 60L
private const val RIVE_VALIDATION_STATE_MACHINE_HANDLE = 70L
private const val RIVE_VALIDATION_VIEW_MODEL_INSTANCE_HANDLE = 80L
private const val RIVE_VALIDATION_CROSS_FILE_VIEW_MODEL_INSTANCE_HANDLE = 81L

class RiveResourceValidationUnitTest : FunSpec({
    test("Rive validation rejects a state machine without its originating artboard") {
        val subject = RiveValidationSubject()

        shouldThrow<RiveIncompatibleResourceException> {
            validateRiveResourceArguments(
                subject.file,
                artboard = null,
                stateMachine = subject.stateMachine,
                viewModelInstance = null,
                globalViewModelInstances = emptyMap(),
            )
        }
    }

    test("Rive validation allows a same-worker VMI for relative cross-file binding") {
        val subject = RiveValidationSubject()
        val crossFileViewModelInstance = ViewModelInstance(
            ViewModelInstanceHandle(RIVE_VALIDATION_CROSS_FILE_VIEW_MODEL_INSTANCE_HANDLE),
            subject.worker,
            FileHandle(RIVE_VALIDATION_OTHER_FILE_HANDLE),
        )

        validateRiveResourceArguments(
            subject.file,
            subject.artboard,
            subject.stateMachine,
            crossFileViewModelInstance,
            emptyMap(),
        )
    }

    test("Rive validation rejects a global VMI from another worker") {
        val subject = RiveValidationSubject()
        val foreignWorker = mockk<CommandQueue>(relaxed = true)
        val foreignInstance = ViewModelInstance(
            ViewModelInstanceHandle(RIVE_VALIDATION_CROSS_FILE_VIEW_MODEL_INSTANCE_HANDLE),
            foreignWorker,
            FileHandle(RIVE_VALIDATION_OTHER_FILE_HANDLE),
        )

        shouldThrow<RiveIncompatibleResourceException> {
            validateRiveResourceArguments(
                subject.file,
                subject.artboard,
                subject.stateMachine,
                viewModelInstance = null,
                globalViewModelInstances = mapOf("Theme" to foreignInstance),
            )
        }
    }

    test("Rive validation rejects a closed global VMI") {
        val subject = RiveValidationSubject()
        subject.viewModelInstance.close()

        shouldThrow<RiveResourceClosedException> {
            validateRiveResourceArguments(
                subject.file,
                subject.artboard,
                subject.stateMachine,
                viewModelInstance = null,
                globalViewModelInstances = mapOf("Theme" to subject.viewModelInstance),
            )
        }
    }

    closedRiveResourceCases.forEach { case ->
        test("Rive validation rejects a closed ${case.name}") {
            val subject = RiveValidationSubject()
            case.close(subject)

            shouldThrow<RiveResourceClosedException> {
                validateRiveResourceArguments(
                    subject.file,
                    subject.artboard,
                    subject.stateMachine,
                    subject.viewModelInstance,
                    emptyMap(),
                )
            }
        }
    }
})

private class RiveValidationSubject {
    val worker = mockk<CommandQueue>(relaxed = true)
    val file = RiveFile(FileHandle(RIVE_VALIDATION_FILE_HANDLE), worker)
    val artboard = Artboard(
        ArtboardHandle(RIVE_VALIDATION_ARTBOARD_HANDLE),
        worker,
        file.fileHandle,
        "Artboard",
    )
    val stateMachine = StateMachine(
        StateMachineHandle(RIVE_VALIDATION_STATE_MACHINE_HANDLE),
        worker,
        artboard.artboardHandle,
        "State Machine",
    )
    val viewModelInstance = ViewModelInstance(
        ViewModelInstanceHandle(RIVE_VALIDATION_VIEW_MODEL_INSTANCE_HANDLE),
        worker,
        file.fileHandle,
    )
}

private data class ClosedRiveResourceCase(
    val name: String,
    val close: (RiveValidationSubject) -> Unit,
)

private val closedRiveResourceCases = listOf(
    ClosedRiveResourceCase("file") { it.file.close() },
    ClosedRiveResourceCase("artboard") { it.artboard.close() },
    ClosedRiveResourceCase("state machine") { it.stateMachine.close() },
    ClosedRiveResourceCase("view model instance") { it.viewModelInstance.close() },
)
