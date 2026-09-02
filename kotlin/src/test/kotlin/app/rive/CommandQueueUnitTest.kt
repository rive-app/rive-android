package app.rive

import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.DefaultViewModelInfo
import app.rive.core.DrawKey
import app.rive.core.FileHandle
import app.rive.core.FrameTicker
import app.rive.core.ImageHandle
import app.rive.core.RenderContext
import app.rive.core.RiveSurface
import app.rive.core.StateMachineHandle
import app.rive.core.ViewModelInstanceHandle
import app.rive.runtime.kotlin.core.ViewModel.PropertyDataType
import app.rive.semantics.SemanticActionType
import app.rive.semantics.SemanticsBoundsUpdate
import app.rive.semantics.SemanticsChildrenUpdate
import app.rive.semantics.SemanticsDiff
import app.rive.semantics.SemanticsDiffNode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

const val VULKAN_RENDER_CONTEXT_ADDR = 3L
const val OPENGL_RENDER_CONTEXT_ADDR = 4L
const val IMAGE_HANDLE_NUM = 654L
const val VALUE_HANDLE_NUM = 789L
val FILE_BYTES = byteArrayOf(0, 1, 2)
private const val TEST_FINAL_RELEASE_SOURCE = "Test final release"

// TODO: Split the remaining tests by functional area:
// - CommandQueueLifecycleUnitTest: construction, backend fallback, tracing, release, and polling.
// - CommandQueueDataBindingUnitTest: view model metadata, instance names, and property setters.
// - RiveSurfaceUnitTest: resizing behavior and the TestRiveSurface fixture.
@OptIn(ExperimentalStdlibApi::class)
class CommandQueueUnitTest : FunSpec({
    val fixture = installCommandQueueTestFixture()
    val renderContextMock = fixture.renderContextMock
    val listenersMock = fixture.listenersMock
    val commandQueueBridgeMock = fixture.commandQueueBridgeMock

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
                HANDLE_NUM
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

    test("Operations on disposed worker throw resource closed") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)

        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

        shouldThrow<RiveResourceClosedException> {
            commandQueue.checkOpen()
        }.message shouldContain "disposed"
        shouldThrow<RiveResourceClosedException> {
            commandQueue.acquire("Disposed worker test")
        }.message shouldContain "disposed"
        shouldThrow<RiveResourceClosedException> {
            commandQueue.release("Disposed worker test")
        }.message shouldContain "disposed"
        shouldThrow<RiveResourceClosedException> {
            commandQueue.setTracingEnabled(true)
        }.message shouldContain "disposed"

        commandQueue.awaitShutdown(1000) shouldBe true
        verify(exactly = 0) {
            commandQueueBridgeMock.cppSetTracingEnabled(COMMAND_QUEUE_ADDR, true)
        }
    }

    // This stands in for suspending command operations, which share the same submission path.
    test("Suspending operation rejects a disposed worker before native submission") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

        shouldThrow<RiveResourceClosedException> {
            commandQueue.loadFile(FILE_BYTES)
        }

        commandQueue.awaitShutdown(1000) shouldBe true
        verify(exactly = 0) {
            commandQueueBridgeMock.cppLoadFile(any(), any(), any())
        }
    }

    // This stands in for fire-and-forget commands, which share the native-pointer entry check.
    test("Fire-and-forget operation rejects a disposed worker before native submission") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

        shouldThrow<RiveResourceClosedException> {
            commandQueue.advanceStateMachine(StateMachineHandle(HANDLE_NUM), 16.milliseconds)
        }

        commandQueue.awaitShutdown(1000) shouldBe true
        verify(exactly = 0) {
            commandQueueBridgeMock.cppAdvanceStateMachine(any(), any(), any(), any())
        }
    }

    test("withLifecycle on disposed worker throws before lifecycle access") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val owner = mockk<LifecycleOwner>()

        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

        shouldThrow<RiveResourceClosedException> {
            commandQueue.withLifecycle(owner, "Disposed worker test")
        }.message shouldContain "disposed"

        commandQueue.awaitShutdown(1000) shouldBe true
        verify(exactly = 0) { owner.lifecycle }
    }

    test("withLifecycle close releases worker once") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val owner = mockk<LifecycleOwner>()
        val lifecycle = mockk<Lifecycle>()
        every { owner.lifecycle } returns lifecycle
        every { lifecycle.currentState } returns Lifecycle.State.CREATED
        every { lifecycle.addObserver(any()) } just runs
        every { lifecycle.removeObserver(any()) } just runs

        val lifecycleHandle = commandQueue.withLifecycle(owner, "Lifecycle owner test")

        lifecycleHandle.close()
        lifecycleHandle.close()

        commandQueue.refCount shouldBe 0
        commandQueue.awaitShutdown(1000) shouldBe true
        verify(exactly = 1) { lifecycle.addObserver(any()) }
        verify(exactly = 1) { lifecycle.removeObserver(any()) }
    }

    test("beginPolling throws if called on disposed worker") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val lifecycle = mockk<Lifecycle>()
        val ticker = FrameTicker { error("Polling should not request a frame after disposal") }

        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)
        commandQueue.awaitShutdown(1000) shouldBe true

        shouldThrow<RiveResourceClosedException> {
            commandQueue.beginPolling(lifecycle, ticker)
        }.message shouldContain "disposed"
        verify(exactly = 0) { commandQueueBridgeMock.cppPollMessages(any()) }
    }

    test("File query failure throws file error") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val fileHandle = FileHandle(HANDLE_NUM)
        val errorMessage = "Invalid file handle"

        every {
            commandQueueBridgeMock.cppGetArtboardNames(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                HANDLE_NUM
            )
        } answers {
            commandQueue.onFileError(requestID.captured, errorMessage)
        }

        shouldThrow<RiveFileException> {
            commandQueue.getArtboardNames(fileHandle)
        }.message shouldContain errorMessage

        verify(exactly = 1) {
            commandQueueBridgeMock.cppGetArtboardNames(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                HANDLE_NUM
            )
        }
    }

    test("Get file assets returns metadata") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val fileHandle = FileHandle(HANDLE_NUM)
        val expected = listOf(
            RiveFileAsset(
                name = "Inter",
                registrationKey = "Inter-43276",
                assetId = 43276,
                cdnUuid = "edcb1816-8405-4983-acd2-16db48d85df4",
                cdnBaseUrl = "https://public.rive.app/cdn/uuid",
                fileExtension = "ttf",
                typeKey = 141,
            )
        )

        every {
            commandQueueBridgeMock.cppGetFileAssets(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                HANDLE_NUM
            )
        } answers {
            commandQueue.onFileAssetsListed(requestID.captured, expected)
        }

        commandQueue.getFileAssets(fileHandle) shouldBe expected

        verify(exactly = 1) {
            commandQueueBridgeMock.cppGetFileAssets(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                HANDLE_NUM
            )
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

        shouldThrow<RiveArtboardException> {
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

    test("Get view model name for instance returns name") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val instanceHandle = ViewModelInstanceHandle(HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppGetViewModelInstanceViewModelName(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                HANDLE_NUM
            )
        } answers {
            commandQueue.onViewModelInstanceViewModelNameReceived(
                requestID.captured,
                "Test All"
            )
        }

        val result = commandQueue.getViewModelInstanceViewModelName(instanceHandle)

        result shouldBe "Test All"
        verify(exactly = 1) {
            commandQueueBridgeMock.cppGetViewModelInstanceViewModelName(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                HANDLE_NUM
            )
        }
    }

    test("Get view model name for instance failure throws view model instance error") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val instanceHandle = ViewModelInstanceHandle(HANDLE_NUM)
        val errorMessage = "Invalid view model instance handle when requesting its view model name"

        every {
            commandQueueBridgeMock.cppGetViewModelInstanceViewModelName(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                HANDLE_NUM
            )
        } answers {
            commandQueue.onViewModelInstanceError(requestID.captured, errorMessage)
        }

        shouldThrow<RiveViewModelInstanceException> {
            commandQueue.getViewModelInstanceViewModelName(instanceHandle)
        }.message shouldContain errorMessage

        verify(exactly = 1) {
            commandQueueBridgeMock.cppGetViewModelInstanceViewModelName(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                HANDLE_NUM
            )
        }
    }

    test("Get view model instance name returns name") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val instanceHandle = ViewModelInstanceHandle(HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppGetViewModelInstanceName(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                HANDLE_NUM
            )
        } answers {
            commandQueue.onViewModelInstanceNameReceived(requestID.captured, "Test Default")
        }

        val result = commandQueue.getViewModelInstanceName(instanceHandle)

        result shouldBe "Test Default"
        verify(exactly = 1) {
            commandQueueBridgeMock.cppGetViewModelInstanceName(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                HANDLE_NUM
            )
        }
    }

    test("Get view model instance name failure throws view model instance error") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val instanceHandle = ViewModelInstanceHandle(HANDLE_NUM)
        val errorMessage = "Invalid view model instance handle when requesting the instance name"

        every {
            commandQueueBridgeMock.cppGetViewModelInstanceName(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                HANDLE_NUM
            )
        } answers {
            commandQueue.onViewModelInstanceError(requestID.captured, errorMessage)
        }

        shouldThrow<RiveViewModelInstanceException> {
            commandQueue.getViewModelInstanceName(instanceHandle)
        }.message shouldContain errorMessage

        verify(exactly = 1) {
            commandQueueBridgeMock.cppGetViewModelInstanceName(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                HANDLE_NUM
            )
        }
    }

    test("Set artboard volume invokes native") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppSetArtboardVolume(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                ARTBOARD_HANDLE_NUM,
                0.75f
            )
        } just runs

        commandQueue.setArtboardVolume(artboardHandle, 0.75f)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppSetArtboardVolume(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                ARTBOARD_HANDLE_NUM,
                0.75f
            )
        }
    }

    test("Get artboard volume returns native value") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppGetArtboardVolume(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                ARTBOARD_HANDLE_NUM
            )
        } answers {
            commandQueue.onArtboardVolumeReceived(requestID.captured, 0.75f)
        }

        val result = commandQueue.getArtboardVolume(artboardHandle)

        result shouldBe 0.75f
        verify(exactly = 1) {
            commandQueueBridgeMock.cppGetArtboardVolume(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                ARTBOARD_HANDLE_NUM
            )
        }
    }

    test("Get artboard volume failure throws artboard error") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val requestID = slot<Long>()
        val artboardHandle = ArtboardHandle(ARTBOARD_HANDLE_NUM)
        val errorMessage = "Failed to get artboard volume"

        every {
            commandQueueBridgeMock.cppGetArtboardVolume(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                ARTBOARD_HANDLE_NUM
            )
        } answers {
            commandQueue.onArtboardError(requestID.captured, errorMessage)
        }

        shouldThrow<RiveArtboardException> {
            commandQueue.getArtboardVolume(artboardHandle)
        }.message shouldContain errorMessage

        verify(exactly = 1) {
            commandQueueBridgeMock.cppGetArtboardVolume(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
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

    test("Unsubscribe from property invokes native") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val instanceHandle = ViewModelInstanceHandle(HANDLE_NUM)
        val propertyPath = "number/path"

        every {
            commandQueueBridgeMock.cppUnsubscribeFromProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                PropertyDataType.NUMBER.value
            )
        } just runs

        commandQueue.unsubscribeFromProperty(
            instanceHandle,
            propertyPath,
            PropertyDataType.NUMBER,
        )

        verify(exactly = 1) {
            commandQueueBridgeMock.cppUnsubscribeFromProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                PropertyDataType.NUMBER.value
            )
        }
    }

    test("Unsubscribe from property rejects a disposed worker") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

        shouldThrow<RiveResourceClosedException> {
            commandQueue.unsubscribeFromProperty(
                ViewModelInstanceHandle(HANDLE_NUM),
                "number/path",
                PropertyDataType.NUMBER,
            )
        }

        commandQueue.awaitShutdown(1000) shouldBe true
        verify(exactly = 0) {
            commandQueueBridgeMock.cppUnsubscribeFromProperty(any(), any(), any(), any())
        }
    }

    test("Set image property invokes native") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val instanceHandle = ViewModelInstanceHandle(HANDLE_NUM)
        val imageHandle = ImageHandle(IMAGE_HANDLE_NUM)
        val propertyPath = "image/path"

        every {
            commandQueueBridgeMock.cppSetImageProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                IMAGE_HANDLE_NUM
            )
        } just runs

        commandQueue.setImageProperty(instanceHandle, propertyPath, imageHandle)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppSetImageProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                IMAGE_HANDLE_NUM
            )
        }
    }

    test("Set image property with null clears native property") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val instanceHandle = ViewModelInstanceHandle(HANDLE_NUM)
        val propertyPath = "image/path"

        every {
            commandQueueBridgeMock.cppSetImageProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                0L
            )
        } just runs

        commandQueue.setImageProperty(instanceHandle, propertyPath, null)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppSetImageProperty(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                propertyPath,
                0L
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

    test("Set view model instance property throws when disposed") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

        shouldThrow<RiveResourceClosedException> {
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

    test("RiveSurface queued resize skips native invalidation after close") {
        val queuedWork = mutableListOf<() -> Unit>()
        every {
            commandQueueBridgeMock.cppRunOnCommandServer(
                COMMAND_QUEUE_ADDR,
                capture(queuedWork),
            )
        } just runs
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val resizeNativeResources = mockk<(Int, Int) -> Unit>(relaxed = true)
        val surface = TestRiveSurface(
            commandQueue,
            width = 100,
            height = 200,
            onResizeNativeResources = resizeNativeResources,
        )

        surface.resize(300, 400)
        surface.close()
        queuedWork shouldHaveSize 2

        queuedWork.first().invoke()

        verify(exactly = 0) { resizeNativeResources(any(), any()) }
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

        shouldThrow<RiveResourceClosedException> {
            surface.resize(300, 400)
        }.message shouldContain "RiveSurface"
        shouldThrow<RiveResourceClosedException> {
            surface.resize(0, 0)
        }
    }

    test("RiveSurface compatibility rejects another CommandQueue") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val foreignQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val surface = TestRiveSurface(commandQueue, width = 100, height = 200)

        surface.requireOwnedBy(commandQueue)
        shouldThrow<RiveIncompatibleResourceException> {
            surface.requireOwnedBy(foreignQueue)
        }.message shouldContain surface.drawKey.handle.toString()
    }

    test("Destroying an owned RiveSurface remains idempotent") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val surface = TestRiveSurface(commandQueue, width = 100, height = 200)

        @Suppress("DEPRECATION")
        commandQueue.destroyRiveSurface(surface)
        @Suppress("DEPRECATION")
        commandQueue.destroyRiveSurface(surface)

        surface.closed shouldBe true
    }

    test("Surface-taking worker operations reject a closed surface before native work") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val surface = TestRiveSurface(commandQueue, width = 100, height = 200).also { it.close() }

        shouldThrow<RiveResourceClosedException> {
            commandQueue.resizeArtboard(ArtboardHandle(ARTBOARD_HANDLE_NUM), surface)
        }
        shouldThrow<RiveResourceClosedException> {
            commandQueue.draw(
                ArtboardHandle(ARTBOARD_HANDLE_NUM),
                StateMachineHandle(HANDLE_NUM),
                surface,
                Fit.Contain(),
            )
        }
        shouldThrow<RiveResourceClosedException> {
            commandQueue.drawToBuffer(
                ArtboardHandle(ARTBOARD_HANDLE_NUM),
                StateMachineHandle(HANDLE_NUM),
                surface,
                ByteArray(4),
                1,
                1,
            )
        }

        verify(exactly = 0) {
            commandQueueBridgeMock.cppResizeArtboard(any(), any(), any(), any(), any())
        }
        verify(exactly = 0) {
            commandQueueBridgeMock.cppDraw(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
        verify(exactly = 0) {
            commandQueueBridgeMock.cppDrawToBuffer(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any()
            )
        }
    }

    test("Surface-taking worker operations reject a surface from another CommandQueue") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val foreignQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val surface = TestRiveSurface(foreignQueue, width = 100, height = 200)

        shouldThrow<RiveIncompatibleResourceException> {
            commandQueue.resizeArtboard(ArtboardHandle(ARTBOARD_HANDLE_NUM), surface)
        }
        shouldThrow<RiveIncompatibleResourceException> {
            commandQueue.draw(
                ArtboardHandle(ARTBOARD_HANDLE_NUM),
                StateMachineHandle(HANDLE_NUM),
                surface,
                Fit.Contain(),
            )
        }
        shouldThrow<RiveIncompatibleResourceException> {
            commandQueue.drawToBuffer(
                ArtboardHandle(ARTBOARD_HANDLE_NUM),
                StateMachineHandle(HANDLE_NUM),
                surface,
                ByteArray(4),
                1,
                1,
            )
        }
        shouldThrow<RiveIncompatibleResourceException> {
            @Suppress("DEPRECATION")
            commandQueue.destroyRiveSurface(surface)
        }

        verify(exactly = 0) {
            commandQueueBridgeMock.cppResizeArtboard(any(), any(), any(), any(), any())
        }
        verify(exactly = 0) {
            commandQueueBridgeMock.cppDraw(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
        verify(exactly = 0) {
            commandQueueBridgeMock.cppDrawToBuffer(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any()
            )
        }
    }

    test("Enable semantics invokes native") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppEnableSemantics(COMMAND_QUEUE_ADDR, HANDLE_NUM)
        } just runs

        commandQueue.enableSemantics(stateMachineHandle)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppEnableSemantics(COMMAND_QUEUE_ADDR, HANDLE_NUM)
        }
    }

    test("Drain semantics diff invokes native with fit mapping") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        val fit = Fit.Contain(Alignment.BottomRight)
        val fitMapping = slot<Byte>()
        val alignmentMapping = slot<Byte>()
        val scaleFactor = slot<Float>()

        every {
            commandQueueBridgeMock.cppDrainSemanticsDiff(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                capture(fitMapping),
                capture(alignmentMapping),
                capture(scaleFactor),
                100f,
                50f
            )
        } just runs

        commandQueue.drainSemanticsDiff(stateMachineHandle, fit, 100f, 50f)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppDrainSemanticsDiff(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                fit.nativeMapping,
                fit.alignment.nativeMapping,
                fit.scaleFactor,
                100f,
                50f
            )
        }
        fitMapping.captured shouldBe fit.nativeMapping
        alignmentMapping.captured shouldBe fit.alignment.nativeMapping
        scaleFactor.captured shouldBe fit.scaleFactor
    }

    test("Fire semantic action invokes native") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppFireSemanticAction(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                17,
                SemanticActionType.Tap.value
            )
        } just runs

        commandQueue.fireSemanticAction(stateMachineHandle, 17, SemanticActionType.Tap)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppFireSemanticAction(
                COMMAND_QUEUE_ADDR,
                HANDLE_NUM,
                17,
                SemanticActionType.Tap.value
            )
        }
    }

    test("Request semantic focus invokes native") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppRequestSemanticFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM, 42)
        } just runs

        commandQueue.requestSemanticFocus(stateMachineHandle, 42)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppRequestSemanticFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM, 42)
        }
    }

    test("Clear semantic focus invokes native") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)

        every {
            commandQueueBridgeMock.cppClearSemanticFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM)
        } just runs

        commandQueue.clearSemanticFocus(stateMachineHandle)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppClearSemanticFocus(COMMAND_QUEUE_ADDR, HANDLE_NUM)
        }
    }

    test("Clear semantic focus rejects a disposed worker before native submission") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        commandQueue.release(TEST_FINAL_RELEASE_SOURCE)

        shouldThrow<RiveResourceClosedException> {
            commandQueue.clearSemanticFocus(stateMachineHandle)
        }

        commandQueue.awaitShutdown(1000) shouldBe true
        verify(exactly = 0) {
            commandQueueBridgeMock.cppClearSemanticFocus(any(), any())
        }
    }

    test("Semantics diff callback applies diff to matching semantic tree") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        val tree = commandQueue.semanticTree(stateMachineHandle)
        tree.nodeCount shouldBe 0

        val diff = SemanticsDiff(
            treeVersion = 1L,
            frameNumber = 1L,
            rootId = 0,
            removed = intArrayOf(),
            added = arrayOf(
                SemanticsDiffNode(
                    id = 1,
                    role = 9,
                    label = "Root",
                    value = "",
                    hint = "",
                    stateFlags = 0,
                    traitFlags = 0,
                    headingLevel = 0,
                    minX = 0f,
                    minY = 0f,
                    maxX = 100f,
                    maxY = 50f,
                    parentId = -1,
                    siblingIndex = 0
                )
            ),
            moved = emptyArray(),
            childrenUpdated = arrayOf(
                SemanticsChildrenUpdate(parentId = -1, childIds = intArrayOf(1))
            ),
            updatedSemantic = emptyArray(),
            updatedGeometry = arrayOf(
                SemanticsBoundsUpdate(id = 1, minX = 0f, minY = 0f, maxX = 100f, maxY = 50f)
            )
        )

        commandQueue.onSemanticsDiffReceived(stateMachineHandle, diff)

        tree.nodeCount shouldBe 1
        tree.nodeById(1)?.label shouldBe "Root"
        tree.roots shouldBe listOf(1)
    }

    test("Delete state machine callback clears maintained semantic tree after queued diffs") {
        val commandQueue = CommandQueue(renderContextMock, commandQueueBridgeMock)
        val stateMachineHandle = StateMachineHandle(HANDLE_NUM)
        val requestID = slot<Long>()

        every {
            commandQueueBridgeMock.cppDeleteStateMachine(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                HANDLE_NUM
            )
        } just runs

        val tree = commandQueue.semanticTree(stateMachineHandle)
        tree.applyDiff(
            SemanticsDiff(
                treeVersion = 1L,
                frameNumber = 1L,
                rootId = 0,
                removed = intArrayOf(),
                added = arrayOf(
                    SemanticsDiffNode(
                        id = 9,
                        role = 9,
                        label = "x",
                        value = "",
                        hint = "",
                        stateFlags = 0,
                        traitFlags = 0,
                        headingLevel = 0,
                        minX = 0f,
                        minY = 0f,
                        maxX = 1f,
                        maxY = 1f,
                        parentId = -1,
                        siblingIndex = 0
                    )
                ),
                moved = emptyArray(),
                childrenUpdated = emptyArray(),
                updatedSemantic = emptyArray(),
                updatedGeometry = emptyArray()
            )
        )
        tree.nodeCount shouldBe 1

        commandQueue.deleteStateMachine(stateMachineHandle)
        commandQueue.semanticTree(stateMachineHandle) shouldBe tree

        commandQueue.onSemanticsDiffReceived(
            stateMachineHandle,
            SemanticsDiff(
                treeVersion = 2L,
                frameNumber = 2L,
                rootId = 0,
                removed = intArrayOf(),
                added = emptyArray(),
                moved = emptyArray(),
                childrenUpdated = emptyArray(),
                updatedSemantic = arrayOf(
                    SemanticsDiffNode(
                        id = 9,
                        role = 9,
                        label = "updated before deletion",
                        value = "",
                        hint = "",
                        stateFlags = 0,
                        traitFlags = 0,
                        headingLevel = 0,
                        minX = 0f,
                        minY = 0f,
                        maxX = 1f,
                        maxY = 1f,
                        parentId = -1,
                        siblingIndex = 0
                    )
                ),
                updatedGeometry = emptyArray()
            )
        )
        tree.nodeById(9)?.label shouldBe "updated before deletion"

        commandQueue.onStateMachineDeleted(stateMachineHandle)
        val replacementTree = commandQueue.semanticTree(stateMachineHandle)

        verify(exactly = 1) {
            commandQueueBridgeMock.cppDeleteStateMachine(COMMAND_QUEUE_ADDR, requestID.captured, HANDLE_NUM)
        }
        (replacementTree === tree) shouldBe false
        replacementTree.nodeCount shouldBe 0
    }
})

internal class TestRiveSurface(
    commandQueue: CommandQueue,
    width: Int,
    height: Int,
    resizable: Boolean = true,
    private val onResizeNativeResources: (Int, Int) -> Unit = { _, _ -> },
) : RiveSurface(
    commandQueue,
    surfaceNativePointer = 30L,
    drawKey = DrawKey(20L),
    width = width,
    height = height,
    resizable = resizable
) {
    override fun resizeNativeResources(width: Int, height: Int) {
        onResizeNativeResources.invoke(width, height)
    }
}
