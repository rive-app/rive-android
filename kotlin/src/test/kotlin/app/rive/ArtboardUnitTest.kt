package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.RiveSurface
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException

private const val TEST_FILE_HANDLE = 123L
private const val TEST_ARTBOARD_HANDLE = 456L

class ArtboardUnitTest : FunSpec({
    test("Close deletes once and reports closed") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val handle = ArtboardHandle(TEST_ARTBOARD_HANDLE)
        val artboard = Artboard(
            handle,
            worker,
            FileHandle(TEST_FILE_HANDLE),
            "Test Artboard",
        )

        artboard.closed shouldBe false
        artboard.close()
        artboard.closed shouldBe true
        artboard.close()

        verify(exactly = 1) { worker.deleteArtboard(handle) }
    }

    test("Factory returns the default artboard") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_FILE_HANDLE), worker)
        val handle = ArtboardHandle(TEST_ARTBOARD_HANDLE)
        coEvery {
            worker.createDefaultArtboardConfirmed(file.fileHandle)
        } returns handle

        val artboard = Artboard.create(file)

        artboard.artboardHandle shouldBe handle
        artboard.name shouldBe null
        coVerify(exactly = 1) {
            worker.createDefaultArtboardConfirmed(file.fileHandle)
        }
    }

    test("Factory returns a named artboard") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_FILE_HANDLE), worker)
        val handle = ArtboardHandle(TEST_ARTBOARD_HANDLE)
        coEvery {
            worker.createArtboardByNameConfirmed(file.fileHandle, "Named Artboard")
        } returns handle

        val artboard = Artboard.create(file, "Named Artboard")

        artboard.artboardHandle shouldBe handle
        artboard.name shouldBe "Named Artboard"
        coVerify(exactly = 1) {
            worker.createArtboardByNameConfirmed(file.fileHandle, "Named Artboard")
        }
    }

    test("Factory propagates creation failure") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_FILE_HANDLE), worker)
        val error = RiveFileException("Missing artboard")
        coEvery {
            worker.createDefaultArtboardConfirmed(file.fileHandle)
        } throws error

        shouldThrow<RiveFileException> {
            Artboard.create(file)
        } shouldBe error
    }

    test("Factory propagates cancellation") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_FILE_HANDLE), worker)
        val cancellation = CancellationException("Cancelled")
        coEvery {
            worker.createDefaultArtboardConfirmed(file.fileHandle)
        } throws cancellation

        shouldThrow<CancellationException> {
            Artboard.create(file)
        } shouldBe cancellation
    }

    test("Factory rejects a closed file before creating an artboard") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_FILE_HANDLE), worker).also { it.close() }

        shouldThrow<RiveResourceClosedException> {
            Artboard.create(file)
        }.message shouldContain TEST_FILE_HANDLE.toString()
        shouldThrow<RiveResourceClosedException> {
            Artboard.create(file, "Named Artboard")
        }.message shouldContain TEST_FILE_HANDLE.toString()

        coVerify(exactly = 0) {
            worker.createDefaultArtboardConfirmed(any())
        }
        coVerify(exactly = 0) {
            worker.createArtboardByNameConfirmed(any(), any())
        }
    }

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

    test("Get state machine names returns the worker result") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val handle = ArtboardHandle(TEST_ARTBOARD_HANDLE)
        val artboard = Artboard(
            handle,
            worker,
            FileHandle(TEST_FILE_HANDLE),
            "Test Artboard",
        )
        val names = listOf("Idle", "Pressed")
        coEvery { worker.getStateMachineNames(handle) } returns names

        artboard.getStateMachineNames() shouldBe names

        coVerify(exactly = 1) { worker.getStateMachineNames(handle) }
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

    test("Resize artboard delegates the surface and scale factor") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val handle = ArtboardHandle(TEST_ARTBOARD_HANDLE)
        val artboard = Artboard(
            handle,
            worker,
            FileHandle(TEST_FILE_HANDLE),
            "Test Artboard",
        )
        val surface = TestRiveSurface(worker, width = 100, height = 200)

        artboard.resizeArtboard(surface, scaleFactor = 2f)

        verify(exactly = 1) { worker.resizeArtboard(handle, surface, 2f) }
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

    test("Reset artboard size delegates to the worker") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val handle = ArtboardHandle(TEST_ARTBOARD_HANDLE)
        val artboard = Artboard(
            handle,
            worker,
            FileHandle(TEST_FILE_HANDLE),
            "Test Artboard",
        )

        artboard.resetArtboardSize()

        verify(exactly = 1) { worker.resetArtboardSize(handle) }
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

    test("Set volume delegates to the worker") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val handle = ArtboardHandle(TEST_ARTBOARD_HANDLE)
        val artboard = Artboard(
            handle,
            worker,
            FileHandle(TEST_FILE_HANDLE),
            "Test Artboard",
        )

        artboard.setVolume(0.25f)

        verify(exactly = 1) { worker.setArtboardVolume(handle, 0.25f) }
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

    test("Get volume returns the worker result") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val handle = ArtboardHandle(TEST_ARTBOARD_HANDLE)
        val artboard = Artboard(
            handle,
            worker,
            FileHandle(TEST_FILE_HANDLE),
            "Test Artboard",
        )
        coEvery { worker.getArtboardVolume(handle) } returns 0.75f

        artboard.getVolume() shouldBe 0.75f

        coVerify(exactly = 1) { worker.getArtboardVolume(handle) }
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
