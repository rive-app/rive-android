@file:Suppress("DEPRECATION")

package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.DefaultViewModelInfo
import app.rive.core.FileHandle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException

private const val TEST_RIVE_FILE_HANDLE = 123L
private const val TEST_RIVE_FILE_ARTBOARD_HANDLE = 456L
private const val TEST_VIEW_MODEL_NAME = "TestViewModel"

class RiveFileUnitTest : FunSpec({
    test("Factory returns a loaded file") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val handle = FileHandle(TEST_RIVE_FILE_HANDLE)
        coEvery { worker.loadFile(any()) } returns handle

        val file = RiveFile.load(RiveFileSource.Bytes(byteArrayOf()), worker)

        file.fileHandle shouldBe handle
        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 0) { worker.release(any(), any()) }
        file.close()
    }

    test("Factory propagates loading failure") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val error = RiveFileException("Invalid file")
        coEvery { worker.loadFile(any()) } throws error

        shouldThrow<RiveFileException> {
            RiveFile.load(RiveFileSource.Bytes(byteArrayOf()), worker)
        } shouldBe error

        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 1) { worker.release(any(), "Load error") }
    }

    test("Factory propagates cancellation") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val cancellation = CancellationException("Cancelled file load")
        coEvery { worker.loadFile(any()) } throws cancellation

        shouldThrow<CancellationException> {
            RiveFile.load(RiveFileSource.Bytes(byteArrayOf()), worker)
        } shouldBe cancellation

        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 1) { worker.release(any(), "Cancellation") }
    }

    test("Factory propagates worker acquisition failure without releasing") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val expected = RiveResourceClosedException("RiveWorker is disposed")
        every { worker.acquire(any()) } throws expected

        shouldThrow<RiveResourceClosedException> {
            RiveFile.load(RiveFileSource.Bytes(byteArrayOf()), worker)
        } shouldBe expected
        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 0) { worker.release(any(), any()) }
        coVerify(exactly = 0) { worker.loadFile(any()) }
    }

    test("Deprecated factory maps loading failure to an error result") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val expected = RiveFileException("Invalid file")
        coEvery { worker.loadFile(any()) } throws expected

        val result = RiveFile.fromSource(RiveFileSource.Bytes(byteArrayOf()), worker)

        result shouldBe Result.Error(expected)
        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 1) { worker.release(any(), "Load error") }
    }

    test("Default view model query rejects a closed artboard before querying worker") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_RIVE_FILE_HANDLE), worker)
        val artboard = Artboard(
            ArtboardHandle(TEST_RIVE_FILE_ARTBOARD_HANDLE),
            worker,
            FileHandle(TEST_RIVE_FILE_HANDLE),
            "Closed Artboard",
        ).also { it.close() }
        clearMocks(worker, answers = false, recordedCalls = true)

        shouldThrow<RiveResourceClosedException> {
            file.getDefaultViewModelInfo(artboard)
        }.message shouldContain TEST_RIVE_FILE_ARTBOARD_HANDLE.toString()

        confirmVerified(worker)
    }

    test("Default view model query rejects an artboard from another file or worker") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val foreignWorker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_RIVE_FILE_HANDLE), worker)
        val siblingFileArtboard = Artboard(
            ArtboardHandle(TEST_RIVE_FILE_ARTBOARD_HANDLE),
            worker,
            FileHandle(TEST_RIVE_FILE_HANDLE + 1),
            "Sibling File Artboard",
        )
        val foreignWorkerArtboard = Artboard(
            ArtboardHandle(TEST_RIVE_FILE_ARTBOARD_HANDLE + 1),
            foreignWorker,
            FileHandle(TEST_RIVE_FILE_HANDLE),
            "Foreign Worker Artboard",
        )

        shouldThrow<RiveIncompatibleResourceException> {
            file.getDefaultViewModelInfo(siblingFileArtboard)
        }.message shouldContain TEST_RIVE_FILE_ARTBOARD_HANDLE.toString()
        shouldThrow<RiveIncompatibleResourceException> {
            file.getDefaultViewModelInfo(foreignWorkerArtboard)
        }.message shouldContain (TEST_RIVE_FILE_ARTBOARD_HANDLE + 1).toString()

        confirmVerified(worker)
        confirmVerified(foreignWorker)
    }

    test("All cached queries throw after close without querying worker again") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val fileHandle = FileHandle(TEST_RIVE_FILE_HANDLE)
        val artboardHandle = ArtboardHandle(TEST_RIVE_FILE_ARTBOARD_HANDLE)
        val file = RiveFile(fileHandle, worker)
        val artboard = Artboard(artboardHandle, worker, fileHandle, "TestArtboard")

        coEvery { worker.getArtboardNames(fileHandle) } returns listOf("TestArtboard")
        coEvery { worker.getViewModelNames(fileHandle) } returns listOf(TEST_VIEW_MODEL_NAME)
        coEvery {
            worker.getViewModelInstanceNames(fileHandle, TEST_VIEW_MODEL_NAME)
        } returns listOf("TestInstance")
        coEvery {
            worker.getViewModelProperties(fileHandle, TEST_VIEW_MODEL_NAME)
        } returns emptyList()
        coEvery { worker.getEnums(fileHandle) } returns emptyList()
        coEvery {
            worker.getDefaultViewModelInfo(fileHandle, artboardHandle)
        } returns DefaultViewModelInfo(TEST_VIEW_MODEL_NAME, "TestInstance")

        file.getArtboardNames()
        file.getViewModelNames()
        file.getViewModelInstanceNames(TEST_VIEW_MODEL_NAME)
        file.getViewModelProperties(TEST_VIEW_MODEL_NAME)
        file.getEnums()
        file.getDefaultViewModelInfo(artboard)
        file.close()

        shouldThrow<RiveResourceClosedException> {
            file.getArtboardNames()
        }.message shouldContain TEST_RIVE_FILE_HANDLE.toString()
        shouldThrow<RiveResourceClosedException> {
            file.getViewModelNames()
        }.message shouldContain TEST_RIVE_FILE_HANDLE.toString()
        shouldThrow<RiveResourceClosedException> {
            file.getViewModelInstanceNames(TEST_VIEW_MODEL_NAME)
        }.message shouldContain TEST_RIVE_FILE_HANDLE.toString()
        shouldThrow<RiveResourceClosedException> {
            file.getViewModelProperties(TEST_VIEW_MODEL_NAME)
        }.message shouldContain TEST_RIVE_FILE_HANDLE.toString()
        shouldThrow<RiveResourceClosedException> {
            file.getEnums()
        }.message shouldContain TEST_RIVE_FILE_HANDLE.toString()
        shouldThrow<RiveResourceClosedException> {
            file.getDefaultViewModelInfo(artboard)
        }.message shouldContain TEST_RIVE_FILE_HANDLE.toString()

        coVerify(exactly = 1) { worker.getArtboardNames(fileHandle) }
        coVerify(exactly = 1) { worker.getViewModelNames(fileHandle) }
        coVerify(exactly = 1) {
            worker.getViewModelInstanceNames(fileHandle, TEST_VIEW_MODEL_NAME)
        }
        coVerify(exactly = 1) {
            worker.getViewModelProperties(fileHandle, TEST_VIEW_MODEL_NAME)
        }
        coVerify(exactly = 1) { worker.getEnums(fileHandle) }
        coVerify(exactly = 1) {
            worker.getDefaultViewModelInfo(fileHandle, artboardHandle)
        }
    }
})
