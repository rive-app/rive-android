package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.RiveSurface
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify

private const val TEST_FILE_HANDLE = 123L
private const val TEST_ARTBOARD_HANDLE = 456L

class ArtboardUnitTest : FunSpec({
    test("Get state machine names throws after close without querying worker") {
        val subject = ClosedArtboardSubject()

        shouldThrow<IllegalStateException> {
            subject.artboard.getStateMachineNames()
        }.message shouldContain "Artboard is closed"

        coVerify(exactly = 0) {
            subject.worker.getStateMachineNames(any())
        }
    }

    test("Resize artboard throws after close without queuing worker command") {
        val subject = ClosedArtboardSubject()
        val surface = mockk<RiveSurface>()

        shouldThrow<IllegalStateException> {
            subject.artboard.resizeArtboard(surface)
        }.message shouldContain "Artboard is closed"

        verify(exactly = 0) {
            subject.worker.resizeArtboard(any(), any(), any())
        }
    }

    test("Reset artboard size throws after close without queuing worker command") {
        val subject = ClosedArtboardSubject()

        shouldThrow<IllegalStateException> {
            subject.artboard.resetArtboardSize()
        }.message shouldContain "Artboard is closed"

        verify(exactly = 0) {
            subject.worker.resetArtboardSize(any())
        }
    }

    test("Set volume throws after close without queuing worker command") {
        val subject = ClosedArtboardSubject()

        shouldThrow<IllegalStateException> {
            subject.artboard.setVolume(0.5f)
        }.message shouldContain "Artboard is closed"

        verify(exactly = 0) {
            subject.worker.setArtboardVolume(any(), any())
        }
    }

    test("Get volume throws after close without querying worker") {
        val subject = ClosedArtboardSubject()

        shouldThrow<IllegalStateException> {
            subject.artboard.getVolume()
        }.message shouldContain "Artboard is closed"

        coVerify(exactly = 0) {
            subject.worker.getArtboardVolume(any())
        }
    }
})

private class ClosedArtboardSubject {
    val worker = mockk<CommandQueue>(relaxed = true)
    val artboard = Artboard(
        ArtboardHandle(TEST_ARTBOARD_HANDLE),
        worker,
        FileHandle(TEST_FILE_HANDLE),
        "Closed Test Artboard",
    ).also { it.close() }
}
