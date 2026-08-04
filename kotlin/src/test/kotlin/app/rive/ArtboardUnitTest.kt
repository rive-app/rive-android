package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.RiveSurface
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify

private const val TEST_FILE_HANDLE = 123L
private const val TEST_ARTBOARD_HANDLE = 456L

class ArtboardUnitTest : FunSpec({
    test("Compatibility helpers reject another worker or file") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val foreignWorker = mockk<CommandQueue>(relaxed = true)
        val artboard = Artboard(
            ArtboardHandle(TEST_ARTBOARD_HANDLE),
            worker,
            FileHandle(TEST_FILE_HANDLE),
            "Test Artboard",
        )

        artboard.requireOwnedBy(worker)
        artboard.requireFromFile(RiveFile(FileHandle(TEST_FILE_HANDLE), worker))

        shouldThrow<RiveIncompatibleResourceException> {
            artboard.requireOwnedBy(foreignWorker)
        }.message shouldContain TEST_ARTBOARD_HANDLE.toString()
        shouldThrow<RiveIncompatibleResourceException> {
            artboard.requireFromFile(RiveFile(FileHandle(TEST_FILE_HANDLE + 1), worker))
        }.message shouldContain TEST_ARTBOARD_HANDLE.toString()
        shouldThrow<RiveIncompatibleResourceException> {
            artboard.requireFromFile(RiveFile(FileHandle(TEST_FILE_HANDLE), foreignWorker))
        }.message shouldContain TEST_ARTBOARD_HANDLE.toString()
    }

    test("Factory rejects a closed file before creating an artboard") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_FILE_HANDLE), worker).also { it.close() }

        shouldThrow<RiveResourceClosedException> {
            Artboard.fromFile(file)
        }.message shouldContain TEST_FILE_HANDLE.toString()
        shouldThrow<RiveResourceClosedException> {
            Artboard.fromFile(file, "Named Artboard")
        }.message shouldContain TEST_FILE_HANDLE.toString()

        verify(exactly = 0) {
            worker.createDefaultArtboard(any())
        }
        verify(exactly = 0) {
            worker.createArtboardByName(any(), any())
        }
    }

    test("Get state machine names throws after close without querying worker") {
        val subject = ClosedArtboardSubject()

        shouldThrow<RiveResourceClosedException> {
            subject.artboard.getStateMachineNames()
        }.message shouldContain TEST_ARTBOARD_HANDLE.toString()

        coVerify(exactly = 0) {
            subject.worker.getStateMachineNames(any())
        }
    }

    test("Resize artboard throws after close without queuing worker command") {
        val subject = ClosedArtboardSubject()
        val surface = mockk<RiveSurface>()

        shouldThrow<RiveResourceClosedException> {
            subject.artboard.resizeArtboard(surface)
        }.message shouldContain TEST_ARTBOARD_HANDLE.toString()

        verify(exactly = 0) {
            subject.worker.resizeArtboard(any(), any(), any())
        }
    }

    test("Resize artboard rejects a closed surface before queuing worker command") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val artboard = Artboard(
            ArtboardHandle(TEST_ARTBOARD_HANDLE),
            worker,
            FileHandle(TEST_FILE_HANDLE),
            "Test Artboard",
        )
        val surface = mockk<RiveSurface>()
        every { surface.checkOpen() } throws RiveResourceClosedException("RiveSurface is closed")

        shouldThrow<RiveResourceClosedException> {
            artboard.resizeArtboard(surface)
        }

        verify(exactly = 0) {
            worker.resizeArtboard(any(), any(), any())
        }
    }

    test("Resize artboard rejects a surface from another worker before queuing a command") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val foreignWorker = mockk<CommandQueue>(relaxed = true)
        val artboard = Artboard(
            ArtboardHandle(TEST_ARTBOARD_HANDLE),
            worker,
            FileHandle(TEST_FILE_HANDLE),
            "Test Artboard",
        )
        val surface = TestRiveSurface(foreignWorker, width = 100, height = 200)

        shouldThrow<RiveIncompatibleResourceException> {
            artboard.resizeArtboard(surface)
        }.message shouldContain surface.drawKey.handle.toString()

        verify(exactly = 0) {
            worker.resizeArtboard(any(), any(), any())
        }
    }

    test("Reset artboard size throws after close without queuing worker command") {
        val subject = ClosedArtboardSubject()

        shouldThrow<RiveResourceClosedException> {
            subject.artboard.resetArtboardSize()
        }.message shouldContain TEST_ARTBOARD_HANDLE.toString()

        verify(exactly = 0) {
            subject.worker.resetArtboardSize(any())
        }
    }

    test("Set volume throws after close without queuing worker command") {
        val subject = ClosedArtboardSubject()

        shouldThrow<RiveResourceClosedException> {
            subject.artboard.setVolume(0.5f)
        }.message shouldContain TEST_ARTBOARD_HANDLE.toString()

        verify(exactly = 0) {
            subject.worker.setArtboardVolume(any(), any())
        }
    }

    test("Get volume throws after close without querying worker") {
        val subject = ClosedArtboardSubject()

        shouldThrow<RiveResourceClosedException> {
            subject.artboard.getVolume()
        }.message shouldContain TEST_ARTBOARD_HANDLE.toString()

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
