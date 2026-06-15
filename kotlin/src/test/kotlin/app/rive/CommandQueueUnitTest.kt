package app.rive

import android.os.Build
import androidx.lifecycle.Lifecycle
import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.CommandQueueBridge
import app.rive.core.DefaultViewModelInfo
import app.rive.core.DrawKey
import app.rive.core.FileHandle
import app.rive.core.FrameTicker
import app.rive.core.Listeners
import app.rive.core.RenderContext
import app.rive.core.RiveSurface
import app.rive.core.ViewModelInstanceHandle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
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
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

const val COMMAND_QUEUE_ADDR = 1L
const val RENDER_CONTEXT_ADDR = 2L
const val VULKAN_RENDER_CONTEXT_ADDR = 3L
const val OPENGL_RENDER_CONTEXT_ADDR = 4L
const val HANDLE_NUM = 123L
const val ARTBOARD_HANDLE_NUM = 456L
const val VALUE_HANDLE_NUM = 789L
val FILE_BYTES = byteArrayOf(0, 1, 2)
private const val TEST_FINAL_RELEASE_SOURCE = "Test final release"

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
        every { commandQueueBridgeMock.isCurrentThreadCommandServer(any()) } returns false
        every {
            commandQueueBridgeMock.cppCreateListeners(
                COMMAND_QUEUE_ADDR,
                any()
            )
        } returns listenersMock
        every { commandQueueBridgeMock.cppSetTracingEnabled(any(), any()) } just runs
        every { commandQueueBridgeMock.cppCancelDraw(any(), any()) } just runs
        every { commandQueueBridgeMock.cppRunOnCommandServer(any(), any()) } just runs
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
            commandQueueBridgeMock.cppSetTracingEnabled(COMMAND_QUEUE_ADDR, false)
        }
        verify(exactly = 1) {
            commandQueueBridgeMock.cppCreateListeners(COMMAND_QUEUE_ADDR, commandQueue)
        }
    }

    test("Constructor closes render context when native startup fails") {
        val expectedError = RiveInitializationException("Command server failed to start")
        every { commandQueueBridgeMock.cppConstructor(RENDER_CONTEXT_ADDR) } throws expectedError

        val error = shouldThrow<RiveInitializationException> {
            CommandQueue(renderContextMock, commandQueueBridgeMock)
        }

        error shouldBe expectedError
        verify(exactly = 1) { renderContextMock.close() }
        verify(exactly = 0) { commandQueueBridgeMock.cppCreateListeners(any(), any()) }
        verify(exactly = 0) { commandQueueBridgeMock.cppSetTracingEnabled(any(), any()) }
        verify(exactly = 0) { commandQueueBridgeMock.cppDelete(any()) }
    }

    test("Constructor suppresses render context close failure on native startup failure") {
        val startupError = RiveInitializationException("Command server failed to start")
        val closeError = RiveShutdownException("Render context close failed")
        every { commandQueueBridgeMock.cppConstructor(RENDER_CONTEXT_ADDR) } throws startupError
        every { renderContextMock.close() } throws closeError

        val error = shouldThrow<RiveInitializationException> {
            CommandQueue(renderContextMock, commandQueueBridgeMock)
        }

        error shouldBe startupError
        error.suppressed shouldHaveSize 1
        error.suppressed.single() shouldBe closeError
        verify(exactly = 1) { renderContextMock.close() }
        verify(exactly = 0) { commandQueueBridgeMock.cppCreateListeners(any(), any()) }
    }

    test("Render backend constructor retries OpenGL when Vulkan startup fails") {
        val vulkanRenderContextMock = mockk<RenderContext>()
        val openGLRenderContextMock = mockk<RenderContext>()
        every { vulkanRenderContextMock.nativeObjectPointer } returns VULKAN_RENDER_CONTEXT_ADDR
        every { vulkanRenderContextMock.close() } just runs
        every { openGLRenderContextMock.nativeObjectPointer } returns OPENGL_RENDER_CONTEXT_ADDR
        every { openGLRenderContextMock.close() } just runs
        every {
            commandQueueBridgeMock.cppConstructor(VULKAN_RENDER_CONTEXT_ADDR)
        } throws RiveInitializationException("Vulkan startup failed")
        every {
            commandQueueBridgeMock.cppConstructor(OPENGL_RENDER_CONTEXT_ADDR)
        } returns COMMAND_QUEUE_ADDR

        val commandQueue = CommandQueue(
            renderBackend = RenderBackend.Vulkan,
            bridge = commandQueueBridgeMock,
            sdkInt = Build.VERSION_CODES.Q,
            renderContextFactory = { backend ->
                when (backend) {
                    RenderBackend.Vulkan -> vulkanRenderContextMock
                    RenderBackend.OpenGL -> openGLRenderContextMock
                }
            }
        )

        commandQueue.refCount shouldBe 1
        verify(exactly = 1) {
            commandQueueBridgeMock.cppConstructor(VULKAN_RENDER_CONTEXT_ADDR)
        }
        verify(exactly = 1) {
            commandQueueBridgeMock.cppConstructor(OPENGL_RENDER_CONTEXT_ADDR)
        }
        verify(exactly = 1) { vulkanRenderContextMock.close() }
        verify(exactly = 0) { openGLRenderContextMock.close() }
    }

    test("Render backend constructor skips Vulkan below API 29") {
        val openGLRenderContextMock = mockk<RenderContext>()
        every { openGLRenderContextMock.nativeObjectPointer } returns OPENGL_RENDER_CONTEXT_ADDR
        every { openGLRenderContextMock.close() } just runs
        every {
            commandQueueBridgeMock.cppConstructor(OPENGL_RENDER_CONTEXT_ADDR)
        } returns COMMAND_QUEUE_ADDR

        CommandQueue(
            renderBackend = RenderBackend.Vulkan,
            bridge = commandQueueBridgeMock,
            sdkInt = Build.VERSION_CODES.P,
            renderContextFactory = { backend ->
                backend shouldBe RenderBackend.OpenGL
                openGLRenderContextMock
            }
        )

        verify(exactly = 1) {
            commandQueueBridgeMock.cppConstructor(OPENGL_RENDER_CONTEXT_ADDR)
        }
        verify(exactly = 0) {
            commandQueueBridgeMock.cppConstructor(VULKAN_RENDER_CONTEXT_ADDR)
        }
    }

    test("Render backend constructor suppresses Vulkan failure when OpenGL retry fails") {
        val vulkanRenderContextMock = mockk<RenderContext>()
        val openGLRenderContextMock = mockk<RenderContext>()
        val vulkanFailure = RiveInitializationException("Vulkan startup failed")
        val openGLFailure = RiveInitializationException("OpenGL startup failed")
        every { vulkanRenderContextMock.nativeObjectPointer } returns VULKAN_RENDER_CONTEXT_ADDR
        every { vulkanRenderContextMock.close() } just runs
        every { openGLRenderContextMock.nativeObjectPointer } returns OPENGL_RENDER_CONTEXT_ADDR
        every { openGLRenderContextMock.close() } just runs
        every {
            commandQueueBridgeMock.cppConstructor(VULKAN_RENDER_CONTEXT_ADDR)
        } throws vulkanFailure
        every {
            commandQueueBridgeMock.cppConstructor(OPENGL_RENDER_CONTEXT_ADDR)
        } throws openGLFailure

        val error = shouldThrow<RiveInitializationException> {
            CommandQueue(
                renderBackend = RenderBackend.Vulkan,
                bridge = commandQueueBridgeMock,
                sdkInt = Build.VERSION_CODES.Q,
                renderContextFactory = { backend ->
                    when (backend) {
                        RenderBackend.Vulkan -> vulkanRenderContextMock
                        RenderBackend.OpenGL -> openGLRenderContextMock
                    }
                }
            )
        }

        error shouldBe openGLFailure
        error.suppressed shouldHaveSize 1
        error.suppressed.single() shouldBe vulkanFailure
        verify(exactly = 1) { vulkanRenderContextMock.close() }
        verify(exactly = 1) { openGLRenderContextMock.close() }
    }

    test("Constructor propagates tracing enabled when requested") {
        CommandQueue(renderContextMock, commandQueueBridgeMock, tracingEnabled = true)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppSetTracingEnabled(
                COMMAND_QUEUE_ADDR,
                true
            )
        }
    }

    test("setTracingEnabled forwards each call") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)

        commandQueue.setTracingEnabled(false)
        commandQueue.setTracingEnabled(true)
        commandQueue.setTracingEnabled(true)

        verify(exactly = 2) {
            commandQueueBridgeMock.cppSetTracingEnabled(
                COMMAND_QUEUE_ADDR,
                false
            )
        }
        verify(exactly = 2) {
            commandQueueBridgeMock.cppSetTracingEnabled(
                COMMAND_QUEUE_ADDR,
                true
            )
        }
    }

    test("Release disposes native resources") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)

        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

        commandQueue.refCount shouldBe 0
        commandQueue.awaitShutdown(1000) shouldBe true
        verify(exactly = 1) { commandQueueBridgeMock.cppDelete(COMMAND_QUEUE_ADDR) }
        verify(exactly = 1) { listenersMock.close() }
        verify(exactly = 1) { renderContextMock.close() }
    }

    test("Release returns before native shutdown completes") {
        val shutdownEntered = CountDownLatch(1)
        val shutdownMayFinish = CountDownLatch(1)
        every { commandQueueBridgeMock.cppDelete(any()) } answers {
            shutdownEntered.countDown()
            shutdownMayFinish.await(2, TimeUnit.SECONDS)
            Unit
        }
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)

        // If release does not return immediately as expected, the test will timeout and fail
        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

        commandQueue.refCount shouldBe 0
        commandQueue.isDisposed shouldBe true
        shutdownEntered.await(1000, TimeUnit.MILLISECONDS) shouldBe true
        // Because the test thread is unblocked, we can verify that shutdown is awaiting the latch.
        commandQueue.awaitShutdown(50) shouldBe false
        verify(exactly = 0) { listenersMock.close() }
        verify(exactly = 0) { renderContextMock.close() }

        shutdownMayFinish.countDown()
        commandQueue.awaitShutdown(1000) shouldBe true
        verify(exactly = 1) { listenersMock.close() }
        verify(exactly = 1) { renderContextMock.close() }
    }

    test("Release cancels pending native continuations immediately") {
        coroutineScope {
            val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
            val submissionStarted = CountDownLatch(1)
            every {
                commandQueueBridgeMock.cppLoadFile(COMMAND_QUEUE_ADDR, any(), FILE_BYTES)
            } answers {
                submissionStarted.countDown()
            }

            // Start immediately so loadFile registers its continuation before release cancels it.
            val load = async(start = CoroutineStart.UNDISPATCHED) {
                shouldThrow<CancellationException> { commandQueue.loadFile(FILE_BYTES) }
            }

            submissionStarted.await(1000, TimeUnit.MILLISECONDS) shouldBe true
            commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

            withTimeout(1_000) {
                load.await()
            }
            commandQueue.awaitShutdown(1000) shouldBe true
        }
    }

    test("Native submission failure propagates") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val expectedError = IllegalStateException("Submission failed")
        every {
            commandQueueBridgeMock.cppLoadFile(COMMAND_QUEUE_ADDR, any(), FILE_BYTES)
        } throws expectedError

        shouldThrow<IllegalStateException> {
            commandQueue.loadFile(FILE_BYTES)
        } shouldBe expectedError
    }

    test("beginPolling throws if called after release") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val lifecycle = mockk<Lifecycle>()
        val ticker = FrameTicker { error("Polling should not request a frame after release") }

        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)
        commandQueue.awaitShutdown(1000) shouldBe true

        shouldThrow<IllegalStateException> {
            commandQueue.beginPolling(lifecycle, ticker)
        }.message shouldContain "released"
        verify(exactly = 0) { commandQueueBridgeMock.cppPollMessages(any()) }
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

    test("Get default view model info returns name and instance") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val fileHandle = FileHandle(HANDLE_NUM)
        val artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppGetDefaultViewModelInfo(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                HANDLE_NUM,
                ARTBOARD_HANDLE_NUM
            )
        } answers {
            commandQueue.onDefaultViewModelInfoReceived(
                requestID.captured,
                "Test All",
                "default"
            )
        }

        val result = commandQueue.getDefaultViewModelInfo(fileHandle, artboardHandle)

        result shouldBe DefaultViewModelInfo("Test All", "default")
        verify(exactly = 1) {
            commandQueueBridgeMock.cppGetDefaultViewModelInfo(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                HANDLE_NUM,
                ARTBOARD_HANDLE_NUM
            )
        }
    }

    test("Get default view model info failure throws artboard error") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val fileHandle = FileHandle(HANDLE_NUM)
        val artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM)
        val errorMessage = "Failed to get default view model info"

        every {
            commandQueueBridgeMock.cppGetDefaultViewModelInfo(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                HANDLE_NUM,
                ARTBOARD_HANDLE_NUM
            )
        } answers {
            commandQueue.onArtboardError(requestID.captured, errorMessage)
        }

        shouldThrow<RuntimeException> {
            commandQueue.getDefaultViewModelInfo(fileHandle, artboardHandle)
        }.message shouldContain errorMessage

        verify(exactly = 1) {
            commandQueueBridgeMock.cppGetDefaultViewModelInfo(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                HANDLE_NUM,
                ARTBOARD_HANDLE_NUM
            )
        }
    }

    test("Set artboard property invokes native") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val instanceHandle = ViewModelInstanceHandle(HANDLE_NUM)
        val artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM)
        val propertyPath = "artboard/path"

        every {
            commandQueueBridgeMock.cppSetArtboardProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                ARTBOARD_HANDLE_NUM
            )
        } just runs

        commandQueue.setArtboardProperty(instanceHandle, propertyPath, artboardHandle)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppSetArtboardProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                ARTBOARD_HANDLE_NUM
            )
        }
    }

    test("Set artboard property with null clears native property") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val instanceHandle = ViewModelInstanceHandle(HANDLE_NUM)
        val propertyPath = "artboard/path"

        every {
            commandQueueBridgeMock.cppSetArtboardProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                0L
            )
        } just runs

        commandQueue.setArtboardProperty(instanceHandle, propertyPath, null)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppSetArtboardProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                0L
            )
        }
    }

    test("Set view model instance property invokes native") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val instanceHandle = ViewModelInstanceHandle(HANDLE_NUM)
        val valueHandle = ViewModelInstanceHandle(VALUE_HANDLE_NUM)
        val propertyPath = "nested/path"

        every {
            commandQueueBridgeMock.cppSetViewModelInstanceProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                VALUE_HANDLE_NUM
            )
        } just runs

        commandQueue.setViewModelInstanceProperty(instanceHandle, propertyPath, valueHandle)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppSetViewModelInstanceProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                VALUE_HANDLE_NUM
            )
        }
    }

    test("Set view model instance property throws when released") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

        shouldThrow<IllegalStateException> {
            commandQueue.setViewModelInstanceProperty(
                ViewModelInstanceHandle(HANDLE_NUM),
                "path",
                ViewModelInstanceHandle(VALUE_HANDLE_NUM)
            )
        }
    }

    test("RiveSurface resize updates dimensions and invalidates render target after canceling draw") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val surface = TestRiveSurface(commandQueue, width = 100, height = 200)

        surface.resize(300, 400)

        surface.width shouldBe 300
        surface.height shouldBe 400
        verifyOrder {
            commandQueueBridgeMock.cppCancelDraw(COMMAND_QUEUE_ADDR, surface.drawKey.handle)
            commandQueueBridgeMock.cppRunOnCommandServer(COMMAND_QUEUE_ADDR, any())
        }
    }

    test("RiveSurface same-size resize is a no-op") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val surface = TestRiveSurface(commandQueue, width = 100, height = 200)

        surface.resize(100, 200)

        verify(exactly = 0) { commandQueueBridgeMock.cppCancelDraw(any(), surface.drawKey.handle) }
        verify(exactly = 0) { commandQueueBridgeMock.cppRunOnCommandServer(any(), any()) }
    }

    test("RiveSurface resize rejects fixed-size surfaces") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val surface = TestRiveSurface(commandQueue, width = 100, height = 200, resizable = false)

        shouldThrow<IllegalStateException> {
            surface.resize(300, 400)
        }.message shouldContain "fixed-size RiveSurface"

        surface.width shouldBe 100
        surface.height shouldBe 200
        verify(exactly = 0) { commandQueueBridgeMock.cppCancelDraw(any(), surface.drawKey.handle) }
        verify(exactly = 0) { commandQueueBridgeMock.cppRunOnCommandServer(any(), any()) }
    }

    test("RiveSurface resize rejects closed surfaces") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val surface = TestRiveSurface(commandQueue, width = 100, height = 200)
        surface.close()

        shouldThrow<IllegalStateException> {
            surface.resize(300, 400)
        }.message shouldContain "closed RiveSurface"
    }
})

private class TestRiveSurface(
    commandQueue: CommandQueue,
    width: Int,
    height: Int,
    resizable: Boolean = true,
) : RiveSurface(
    commandQueue,
    surfaceNativePointer = 30L,
    drawKey = DrawKey(20L),
    width = width,
    height = height,
    resizable = resizable
)
