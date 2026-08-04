@file:Suppress("DEPRECATION")
@file:OptIn(ExperimentalHardwareBitmapRendering::class)

package app.rive

import android.graphics.Bitmap
import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.StateMachineHandle
import app.rive.core.ViewModelInstanceHandle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk

private const val RENDER_FILE_HANDLE = 10L
private const val RENDER_ARTBOARD_HANDLE = 20L
private const val RENDER_STATE_MACHINE_HANDLE = 30L

class RenderResourceValidationUnitTest : FunSpec({
    test("Software render buffer rejects closed resource arguments before bitmap validation") {
        val subject = RenderValidationSubject()
        val buffer = subject.softwareBuffer()
        subject.artboard.close()

        shouldThrow<RiveResourceClosedException> {
            buffer.renderInto(mockk(), subject.artboard, subject.stateMachine)
        }
    }

    test("Render buffers reject a closed state machine") {
        val subject = RenderValidationSubject()
        subject.stateMachine.close()

        shouldThrow<RiveResourceClosedException> {
            subject.softwareBuffer().renderInto(mockk(), subject.artboard, subject.stateMachine)
        }
        shouldThrow<RiveResourceClosedException> {
            subject.renderBuffer().render(subject.artboard, subject.stateMachine)
        }
    }

    test("Software render buffer rejects rendering after close") {
        val subject = RenderValidationSubject()
        val buffer = subject.softwareBuffer().also { it.close() }

        shouldThrow<RiveResourceClosedException> {
            buffer.renderInto(mockk(), subject.artboard, subject.stateMachine)
        }
    }

    test("Software render buffer rejects incompatible resource arguments") {
        val subject = RenderValidationSubject()
        val buffer = subject.softwareBuffer()

        shouldThrow<RiveIncompatibleResourceException> {
            buffer.renderInto(mockk(), subject.foreignArtboard, subject.stateMachine)
        }
        shouldThrow<RiveIncompatibleResourceException> {
            buffer.renderInto(mockk(), subject.artboard, subject.foreignStateMachine)
        }
        shouldThrow<RiveIncompatibleResourceException> {
            buffer.renderInto(mockk(), subject.artboard, subject.siblingStateMachine)
        }
    }

    test("Deprecated render buffer entry points reject incompatible resources") {
        val subject = RenderValidationSubject()
        val buffer = subject.renderBuffer()

        shouldThrow<RiveIncompatibleResourceException> {
            buffer.render(subject.foreignArtboard, subject.stateMachine)
        }
        shouldThrow<RiveIncompatibleResourceException> {
            buffer.snapshot(subject.artboard, subject.foreignStateMachine)
        }
        shouldThrow<RiveIncompatibleResourceException> {
            buffer.render(subject.artboard, subject.siblingStateMachine)
        }
    }

    test("Deprecated render buffer operations reject use after close") {
        val subject = RenderValidationSubject()
        val buffer = subject.renderBuffer().also { it.close() }

        shouldThrow<RiveResourceClosedException> {
            buffer.render(subject.artboard, subject.stateMachine)
        }
        shouldThrow<RiveResourceClosedException> {
            buffer.snapshot(subject.artboard, subject.stateMachine)
        }
        shouldThrow<RiveResourceClosedException> {
            buffer.copyInto(mockk())
        }
        shouldThrow<RiveResourceClosedException> {
            buffer.toBitmap()
        }
    }

    test("Canvas session rejects a closed artboard before platform support validation") {
        val subject = RenderValidationSubject()
        subject.artboard.close()

        shouldThrow<RiveResourceClosedException> {
            RiveCanvasSession(
                subject.worker,
                subject.artboard,
                subject.stateMachine,
                subject.viewModelInstance,
            )
        }
    }

    test("Canvas session rejects a closed state machine before platform support validation") {
        val subject = RenderValidationSubject()
        subject.stateMachine.close()

        shouldThrow<RiveResourceClosedException> {
            RiveCanvasSession(
                subject.worker,
                subject.artboard,
                subject.stateMachine,
                subject.viewModelInstance,
            )
        }
    }

    test("Canvas session rejects a closed view model instance before platform validation") {
        val subject = RenderValidationSubject()
        subject.viewModelInstance.close()

        shouldThrow<RiveResourceClosedException> {
            RiveCanvasSession(
                subject.worker,
                subject.artboard,
                subject.stateMachine,
                subject.viewModelInstance,
            )
        }
    }

    test("Canvas session rejects a disposed worker before platform support validation") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val artboard = Artboard(
            ArtboardHandle(RENDER_ARTBOARD_HANDLE),
            worker,
            FileHandle(RENDER_FILE_HANDLE),
            "Artboard",
        )
        val stateMachine = StateMachine(
            StateMachineHandle(RENDER_STATE_MACHINE_HANDLE),
            worker,
            artboard.artboardHandle,
            "State Machine",
        )
        every { worker.checkOpen() } throws RiveResourceClosedException("RiveWorker is disposed")

        shouldThrow<RiveResourceClosedException> {
            RiveCanvasSession(worker, artboard, stateMachine)
        }
    }

    test("Canvas session rejects incompatible resources before platform support validation") {
        val subject = RenderValidationSubject()

        shouldThrow<RiveIncompatibleResourceException> {
            RiveCanvasSession(subject.worker, subject.foreignArtboard, subject.stateMachine)
        }
        shouldThrow<RiveIncompatibleResourceException> {
            RiveCanvasSession(subject.worker, subject.artboard, subject.foreignStateMachine)
        }
        shouldThrow<RiveIncompatibleResourceException> {
            RiveCanvasSession(subject.worker, subject.artboard, subject.siblingStateMachine)
        }
        shouldThrow<RiveIncompatibleResourceException> {
            RiveCanvasSession(
                subject.worker,
                subject.artboard,
                subject.stateMachine,
                ViewModelInstance(
                    ViewModelInstanceHandle(40L),
                    subject.foreignWorker,
                    FileHandle(RENDER_FILE_HANDLE),
                ),
            )
        }
    }

    test("Hardware render buffer retains its unsupported hardware exception category") {
        val subject = RenderValidationSubject()

        shouldThrow<IllegalStateException> {
            HardwareRenderBuffer(1, 1, subject.worker)
        }
    }

    test("Canvas session retains its unsupported hardware exception category") {
        val subject = RenderValidationSubject()

        shouldThrow<IllegalStateException> {
            RiveCanvasSession(subject.worker, subject.artboard, subject.stateMachine)
        }
    }
})

private class RenderValidationSubject {
    val worker = mockk<CommandQueue>(relaxed = true)
    val foreignWorker = mockk<CommandQueue>(relaxed = true)
    val artboard = Artboard(
        ArtboardHandle(RENDER_ARTBOARD_HANDLE),
        worker,
        FileHandle(RENDER_FILE_HANDLE),
        "Artboard",
    )
    val foreignArtboard = Artboard(
        ArtboardHandle(RENDER_ARTBOARD_HANDLE + 1),
        foreignWorker,
        FileHandle(RENDER_FILE_HANDLE),
        "Foreign Artboard",
    )
    val stateMachine = StateMachine(
        StateMachineHandle(RENDER_STATE_MACHINE_HANDLE),
        worker,
        artboard.artboardHandle,
        "State Machine",
    )
    val foreignStateMachine = StateMachine(
        StateMachineHandle(RENDER_STATE_MACHINE_HANDLE + 1),
        foreignWorker,
        foreignArtboard.artboardHandle,
        "Foreign State Machine",
    )
    val siblingStateMachine = StateMachine(
        StateMachineHandle(RENDER_STATE_MACHINE_HANDLE + 2),
        worker,
        ArtboardHandle(RENDER_ARTBOARD_HANDLE + 2),
        "Sibling State Machine",
    )
    val viewModelInstance = ViewModelInstance(
        ViewModelInstanceHandle(40L),
        worker,
        FileHandle(RENDER_FILE_HANDLE),
    )

    init {
        every { worker.createImageSurface(1, 1) } answers {
            TestRiveSurface(worker, width = 1, height = 1, resizable = false)
        }
    }

    /** @return A software render buffer owned by [worker]. */
    fun softwareBuffer() = SoftwareRenderBuffer(1, 1, worker)

    /** @return A deprecated render buffer owned by [worker]. */
    fun renderBuffer() = RenderBuffer(1, 1, worker)
}
