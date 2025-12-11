package app.rive

import app.rive.core.CommandQueue
import app.rive.core.CommandQueueBridge
import app.rive.core.FileHandle
import app.rive.core.Listeners
import app.rive.core.RenderContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

const val COMMAND_QUEUE_ADDR = 1L
const val RENDER_CONTEXT_ADDR = 2L
const val HANDLE_NUM = 123L
val FILE_BYTES = byteArrayOf(0, 1, 2)

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
class CommandQueueUnitTest : FunSpec({
    val renderContextMock = mockk<RenderContext>()
    val listenersMock = mockk<Listeners>()
    val commandQueueBridgeMock = mockk<CommandQueueBridge>()

    beforeTest {
        // Because CommandQueue forces coroutines to Main, we have to set Main to a test dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())

        every { renderContextMock.nativeObjectPointer } returns RENDER_CONTEXT_ADDR
        every { renderContextMock.close() } just runs

        every { listenersMock.close() } just runs

        every { commandQueueBridgeMock.cppConstructor(any()) } returns COMMAND_QUEUE_ADDR
        every { commandQueueBridgeMock.cppDelete(any()) } just runs
        every {
            commandQueueBridgeMock.cppCreateListeners(
                COMMAND_QUEUE_ADDR,
                any()
            )
        } returns listenersMock
    }

    afterTest {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    test("Constructor invokes native setup") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)

        commandQueue.refCount shouldBe 1
        commandQueue.isDisposed shouldBe false
        verify(exactly = 1) { commandQueueBridgeMock.cppConstructor(RENDER_CONTEXT_ADDR) }
        verify(exactly = 1) {
            commandQueueBridgeMock.cppCreateListeners(
                COMMAND_QUEUE_ADDR,
                commandQueue
            )
        }
    }

    test("Release disposes native resources") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)

        commandQueue.release("")

        commandQueue.refCount shouldBe 0
        verify(exactly = 1) { commandQueueBridgeMock.cppDelete(COMMAND_QUEUE_ADDR) }
        verify(exactly = 1) { listenersMock.close() }
    }

    test("Load file invokes native method and returns handle") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val bytes = FILE_BYTES
        val requestID = slot<Long>()
        val expected = FileHandle(HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppLoadFile(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                bytes
            )
        } answers {
            commandQueue.onFileLoaded(requestID.captured, expected)
        }

        val result = commandQueue.loadFile(bytes)

        result shouldBe expected
        verify(exactly = 1) {
            commandQueueBridgeMock.cppLoadFile(COMMAND_QUEUE_ADDR, requestID.captured, bytes)
        }
    }

    test("Load file failure invokes native method and throws error") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val bytes = FILE_BYTES
        val requestID = slot<Long>()
        val errorMessage = "Failed to load"

        every {
            commandQueueBridgeMock.cppLoadFile(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                bytes
            )
        } answers {
            commandQueue.onFileError(requestID.captured, errorMessage)
        }

        shouldThrow<RiveFileException> { commandQueue.loadFile(bytes) }.message shouldContain errorMessage
        verify(exactly = 1) {
            commandQueueBridgeMock.cppLoadFile(COMMAND_QUEUE_ADDR, requestID.captured, bytes)
        }
    }

    test("Load file failure clears continuation; subsequent load succeeds") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val expected = FileHandle(HANDLE_NUM)
        val requestIDs = mutableListOf<Long>()

        every {
            commandQueueBridgeMock.cppLoadFile(
                COMMAND_QUEUE_ADDR,
                capture(requestIDs),
                FILE_BYTES
            )
        } answers {
            // First call simulates error
            commandQueue.onFileError(requestIDs.last(), "Failed to load")
        } andThenAnswer {
            // Second call simulates success
            commandQueue.onFileLoaded(requestIDs.last(), expected)
        }

        // First call throws
        shouldThrow<RiveFileException> { commandQueue.loadFile(FILE_BYTES) }
        // Second call succeeds
        val result = commandQueue.loadFile(FILE_BYTES)

        result shouldBe expected
        verify(exactly = 2) {
            commandQueueBridgeMock.cppLoadFile(COMMAND_QUEUE_ADDR, any(), FILE_BYTES)
        }
        requestIDs[0] shouldBeLessThan requestIDs[1]
    }

    test("Delete file invokes native") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val fileHandle = FileHandle(HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppDeleteFile(COMMAND_QUEUE_ADDR, capture(requestID), HANDLE_NUM)
        } just runs

        commandQueue.deleteFile(fileHandle)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppDeleteFile(COMMAND_QUEUE_ADDR, requestID.captured, HANDLE_NUM)
        }
    }
})
