package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.AudioHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.FontHandle
import app.rive.core.ImageHandle
import app.rive.core.StateMachineHandle
import app.rive.core.StateMachineSettlingStore
import app.rive.core.ViewModelInstanceHandle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlin.concurrent.thread

private const val CONFIRMED_FILE_HANDLE = 100L
private const val CONFIRMED_ARTBOARD_HANDLE = 200L
private const val CONFIRMED_STATE_MACHINE_HANDLE = 300L
private const val CONFIRMED_VMI_HANDLE = 400L
private const val CONFIRMED_IMAGE_HANDLE = 500L
private const val CONFIRMED_AUDIO_HANDLE = 600L
private const val CONFIRMED_FONT_HANDLE = 700L

/** Tests creation, cancellation cleanup, and deletion for command-queue-owned resources. */
@OptIn(ExperimentalCoroutinesApi::class)
class CommandQueueResourceLifecycleUnitTest : FunSpec({
    val fixture = installCommandQueueTestFixture()
    val renderContext = fixture.renderContextMock
    val bridge = fixture.commandQueueBridgeMock

    test("File creation waits for native success") {
        val queue = CommandQueue(renderContext, bridge)
        val requestID = slot<Long>()
        val expected = FileHandle(CONFIRMED_FILE_HANDLE)
        every {
            bridge.cppLoadFile(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
        } answers {
            queue.onFileLoaded(requestID.captured, expected)
            expected.handle
        }

        queue.loadFile(FILE_BYTES) shouldBe expected
        verify(exactly = 1) {
            bridge.cppLoadFile(COMMAND_QUEUE_ADDR, requestID.captured, FILE_BYTES)
        }
    }

    test("File creation reports native failure") {
        val queue = CommandQueue(renderContext, bridge)
        val requestID = slot<Long>()
        val errorMessage = "Failed to load"
        every {
            bridge.cppLoadFile(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
        } answers {
            queue.onFileError(requestID.captured, errorMessage)
            CONFIRMED_FILE_HANDLE
        }

        shouldThrow<RiveFileException> {
            queue.loadFile(FILE_BYTES)
        }.message shouldContain errorMessage
        verify(exactly = 1) {
            bridge.cppLoadFile(COMMAND_QUEUE_ADDR, requestID.captured, FILE_BYTES)
        }
    }

    test("File creation failure clears its continuation") {
        val queue = CommandQueue(renderContext, bridge)
        val expected = FileHandle(CONFIRMED_FILE_HANDLE)
        val requestIDs = mutableListOf<Long>()
        every {
            bridge.cppLoadFile(COMMAND_QUEUE_ADDR, capture(requestIDs), FILE_BYTES)
        } answers {
            queue.onFileError(requestIDs.last(), "Failed to load")
            expected.handle
        } andThenAnswer {
            queue.onFileLoaded(requestIDs.last(), expected)
            expected.handle
        }

        shouldThrow<RiveFileException> { queue.loadFile(FILE_BYTES) }
        queue.loadFile(FILE_BYTES) shouldBe expected

        verify(exactly = 2) {
            bridge.cppLoadFile(COMMAND_QUEUE_ADDR, any(), FILE_BYTES)
        }
        requestIDs[0] shouldBeLessThan requestIDs[1]
    }

    test("File creation propagates native submission failure") {
        val queue = CommandQueue(renderContext, bridge)
        val expectedError = IllegalStateException("Submission failed")
        every {
            bridge.cppLoadFile(COMMAND_QUEUE_ADDR, any(), FILE_BYTES)
        } throws expectedError

        shouldThrow<IllegalStateException> {
            queue.loadFile(FILE_BYTES)
        } shouldBe expectedError
    }

    test("File creation cancelled before Main submission does not invoke native") {
        coroutineScope {
            val mainDispatcher = StandardTestDispatcher()
            Dispatchers.setMain(mainDispatcher)
            val queue = CommandQueue(renderContext, bridge)

            val load = async(start = CoroutineStart.UNDISPATCHED) {
                queue.loadFile(FILE_BYTES)
            }
            load.cancelAndJoin()
            mainDispatcher.scheduler.advanceUntilIdle()

            verify(exactly = 0) {
                bridge.cppLoadFile(any(), any(), any())
            }
            verify(exactly = 0) {
                bridge.cppDeleteFile(any(), any(), any())
            }
        }
    }

    test("Cancelled file creation deletes its provisional handle once") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = FileHandle(CONFIRMED_FILE_HANDLE)
            every {
                bridge.cppLoadFile(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
            } returns expected.handle
            every {
                bridge.cppDeleteFile(COMMAND_QUEUE_ADDR, any(), expected.handle)
            } just runs

            val load = async(start = CoroutineStart.UNDISPATCHED) {
                queue.loadFile(FILE_BYTES)
            }
            load.cancelAndJoin()

            verify(exactly = 1) {
                bridge.cppDeleteFile(COMMAND_QUEUE_ADDR, any(), expected.handle)
            }

            // A late success must not schedule a second deletion.
            queue.onFileLoaded(requestID.captured, expected)

            verify(exactly = 1) {
                bridge.cppDeleteFile(COMMAND_QUEUE_ADDR, any(), expected.handle)
            }
        }
    }

    test("File creation cancelled while returning confirmation deletes its handle") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = FileHandle(CONFIRMED_FILE_HANDLE)
            val callerDispatcher = StandardTestDispatcher()
            every {
                bridge.cppLoadFile(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
            } returns expected.handle
            every {
                bridge.cppDeleteFile(COMMAND_QUEUE_ADDR, any(), expected.handle)
            } just runs

            val load = async(callerDispatcher, start = CoroutineStart.UNDISPATCHED) {
                queue.loadFile(FILE_BYTES)
            }
            queue.onFileLoaded(requestID.captured, expected)

            // Native confirmation has resumed the request, but delivery to the caller remains
            // queued on its dispatcher.
            load.isCompleted shouldBe false
            load.cancel()
            callerDispatcher.scheduler.advanceUntilIdle()
            load.join()

            verify(exactly = 1) {
                bridge.cppDeleteFile(COMMAND_QUEUE_ADDR, any(), expected.handle)
            }
        }
    }

    test("File deletion invokes native") {
        val queue = CommandQueue(renderContext, bridge)
        val requestID = slot<Long>()
        val fileHandle = FileHandle(CONFIRMED_FILE_HANDLE)
        every {
            bridge.cppDeleteFile(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                fileHandle.handle
            )
        } just runs

        queue.deleteFile(fileHandle)

        verify(exactly = 1) {
            bridge.cppDeleteFile(COMMAND_QUEUE_ADDR, requestID.captured, fileHandle.handle)
        }
    }

    test("Artboard creation waits for native success") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = ArtboardHandle(CONFIRMED_ARTBOARD_HANDLE)
            every {
                bridge.cppCreateDefaultArtboard(
                    COMMAND_QUEUE_ADDR,
                    capture(requestID),
                    CONFIRMED_FILE_HANDLE
                )
            } returns expected.handle

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.createDefaultArtboardConfirmed(FileHandle(CONFIRMED_FILE_HANDLE))
            }

            creation.isCompleted.shouldBeFalse()
            queue.onArtboardInstantiated(requestID.captured, expected)

            creation.await() shouldBe expected
            verify(exactly = 0) { bridge.cppDeleteArtboard(any(), any(), any()) }
        }
    }

    test("Artboard creation reports native failure") {
        val queue = CommandQueue(renderContext, bridge)
        val requestID = slot<Long>()
        every {
            bridge.cppCreateArtboardByName(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                CONFIRMED_FILE_HANDLE,
                "Missing"
            )
        } answers {
            queue.onFileError(requestID.captured, "artboard not found")
            CONFIRMED_ARTBOARD_HANDLE
        }

        shouldThrow<RiveFileException> {
            queue.createArtboardByNameConfirmed(
                FileHandle(CONFIRMED_FILE_HANDLE),
                "Missing"
            )
        }.message shouldContain "artboard not found"

        verify(exactly = 0) { bridge.cppDeleteArtboard(any(), any(), any()) }
    }

    test("Cancelled artboard creation deletes its provisional handle once") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = ArtboardHandle(CONFIRMED_ARTBOARD_HANDLE)
            every {
                bridge.cppCreateDefaultArtboard(
                    COMMAND_QUEUE_ADDR,
                    capture(requestID),
                    CONFIRMED_FILE_HANDLE
                )
            } returns expected.handle
            every {
                bridge.cppDeleteArtboard(COMMAND_QUEUE_ADDR, any(), expected.handle)
            } just runs

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.createDefaultArtboardConfirmed(FileHandle(CONFIRMED_FILE_HANDLE))
            }
            creation.cancelAndJoin()

            // A late success must not schedule a second deletion.
            queue.onArtboardInstantiated(requestID.captured, expected)

            verify(exactly = 1) {
                bridge.cppDeleteArtboard(COMMAND_QUEUE_ADDR, any(), expected.handle)
            }
        }
    }

    test("Artboard deletion invokes native") {
        val queue = CommandQueue(renderContext, bridge)
        val requestID = slot<Long>()
        val artboardHandle = ArtboardHandle(CONFIRMED_ARTBOARD_HANDLE)
        every {
            bridge.cppDeleteArtboard(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                artboardHandle.handle
            )
        } just runs

        queue.deleteArtboard(artboardHandle)

        verify(exactly = 1) {
            bridge.cppDeleteArtboard(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                artboardHandle.handle
            )
        }
    }

    test("State machine creation registers settled state only after success") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = StateMachineHandle(CONFIRMED_STATE_MACHINE_HANDLE)
            every {
                bridge.cppCreateDefaultStateMachine(
                    COMMAND_QUEUE_ADDR,
                    capture(requestID),
                    CONFIRMED_ARTBOARD_HANDLE
                )
            } returns expected.handle

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.createDefaultStateMachineConfirmed(
                    ArtboardHandle(CONFIRMED_ARTBOARD_HANDLE)
                )
            }

            shouldThrow<IllegalStateException> { queue.stateMachineSettled(expected) }
            queue.onStateMachineInstantiated(requestID.captured, expected)

            creation.await() shouldBe expected
            queue.stateMachineSettled(expected).value shouldBe false
        }
    }

    test("Cancelled state machine creation deletes its provisional handle once") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = StateMachineHandle(CONFIRMED_STATE_MACHINE_HANDLE)
            every {
                bridge.cppCreateDefaultStateMachine(
                    COMMAND_QUEUE_ADDR,
                    capture(requestID),
                    CONFIRMED_ARTBOARD_HANDLE
                )
            } returns expected.handle
            every {
                bridge.cppDeleteStateMachine(COMMAND_QUEUE_ADDR, any(), expected.handle)
            } just runs

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.createDefaultStateMachineConfirmed(
                    ArtboardHandle(CONFIRMED_ARTBOARD_HANDLE)
                )
            }
            creation.cancelAndJoin()
            queue.onStateMachineInstantiated(requestID.captured, expected)

            verify(exactly = 1) {
                bridge.cppDeleteStateMachine(COMMAND_QUEUE_ADDR, any(), expected.handle)
            }
            shouldThrow<IllegalStateException> { queue.stateMachineSettled(expected) }
        }
    }

    test("Cancellation racing state machine confirmation does not retain settled state") {
        coroutineScope {
            val registrationStarted = CountDownLatch(1)
            val continueRegistration = CountDownLatch(1)
            val confirmationFailure = AtomicReference<Throwable?>(null)
            val settlingStore = spyk(StateMachineSettlingStore { 0L })
            val requestID = slot<Long>()
            val expected = StateMachineHandle(CONFIRMED_STATE_MACHINE_HANDLE)
            val queue = CommandQueue(
                renderContext,
                bridge,
                settlingStore = settlingStore,
            )
            every {
                bridge.cppCreateDefaultStateMachine(
                    COMMAND_QUEUE_ADDR,
                    capture(requestID),
                    CONFIRMED_ARTBOARD_HANDLE
                )
            } returns expected.handle
            every {
                bridge.cppDeleteStateMachine(COMMAND_QUEUE_ADDR, any(), expected.handle)
            } just runs
            every {
                settlingStore.register(any())
            } answers {
                firstArg<Long>() shouldBe expected.handle // MockK sees the unboxed value class.
                // Pause after confirmation selects settled tracking but before it mutates the store.
                registrationStarted.countDown()
                check(continueRegistration.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to continue settled-state registration"
                }
                callOriginal()
            }

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.createDefaultStateMachineConfirmed(
                    ArtboardHandle(CONFIRMED_ARTBOARD_HANDLE)
                )
            }
            val confirmation = thread(name = "state-machine-confirmation") {
                try {
                    queue.onStateMachineInstantiated(requestID.captured, expected)
                } catch (t: Throwable) {
                    confirmationFailure.set(t)
                }
            }

            try {
                check(registrationStarted.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting for settled-state registration; " +
                        "confirmation failure: ${confirmationFailure.get()}"
                }
                // Cancellation deletes and unregisters the state machine while registration waits.
                creation.cancelAndJoin()
                // The late registration must be compensated when delivery finds no continuation.
                continueRegistration.countDown()
                confirmation.join(5_000)

                confirmation.isAlive shouldBe false
                confirmationFailure.get()?.let { failure ->
                    throw AssertionError("State machine confirmation failed", failure)
                }
                verify(exactly = 1) {
                    bridge.cppDeleteStateMachine(COMMAND_QUEUE_ADDR, any(), expected.handle)
                }
                shouldThrow<IllegalStateException> { queue.stateMachineSettled(expected) }
            } finally {
                creation.cancelAndJoin()
                continueRegistration.countDown()
                confirmation.join(5_000)
            }
        }
    }

    test("State machine deletion invokes native and unregisters settled state") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val stateMachineHandle = StateMachineHandle(CONFIRMED_STATE_MACHINE_HANDLE)
            every {
                bridge.cppCreateDefaultStateMachine(
                    COMMAND_QUEUE_ADDR,
                    capture(requestID),
                    CONFIRMED_ARTBOARD_HANDLE
                )
            } returns stateMachineHandle.handle
            every {
                bridge.cppDeleteStateMachine(
                    COMMAND_QUEUE_ADDR,
                    any(),
                    stateMachineHandle.handle
                )
            } just runs

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.createDefaultStateMachineConfirmed(
                    ArtboardHandle(CONFIRMED_ARTBOARD_HANDLE)
                )
            }
            queue.onStateMachineInstantiated(requestID.captured, stateMachineHandle)
            creation.await()

            queue.deleteStateMachine(stateMachineHandle)

            verify(exactly = 1) {
                bridge.cppDeleteStateMachine(
                    COMMAND_QUEUE_ADDR,
                    any(),
                    stateMachineHandle.handle
                )
            }
            shouldThrow<IllegalStateException> { queue.stateMachineSettled(stateMachineHandle) }
        }
    }

    test("View model instance creation waits for native success") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = ViewModelInstanceHandle(CONFIRMED_VMI_HANDLE)
            every {
                bridge.cppNamedVMCreateBlankVMI(
                    COMMAND_QUEUE_ADDR,
                    capture(requestID),
                    CONFIRMED_FILE_HANDLE,
                    "Test VM"
                )
            } returns expected.handle

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.createViewModelInstanceConfirmed(
                    FileHandle(CONFIRMED_FILE_HANDLE),
                    ViewModelSource.Named("Test VM").blankInstance()
                )
            }

            creation.isCompleted.shouldBeFalse()
            queue.onViewModelInstanceInstantiated(requestID.captured, expected)

            creation.await() shouldBe expected
            verify(exactly = 0) { bridge.cppDeleteViewModelInstance(any(), any(), any()) }
        }
    }

    test("Cancelled view model instance creation deletes its provisional handle once") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = ViewModelInstanceHandle(CONFIRMED_VMI_HANDLE)
            every {
                bridge.cppNamedVMCreateBlankVMI(
                    COMMAND_QUEUE_ADDR,
                    capture(requestID),
                    CONFIRMED_FILE_HANDLE,
                    "Test VM"
                )
            } returns expected.handle
            every {
                bridge.cppDeleteViewModelInstance(COMMAND_QUEUE_ADDR, any(), expected.handle)
            } just runs

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.createViewModelInstanceConfirmed(
                    FileHandle(CONFIRMED_FILE_HANDLE),
                    ViewModelSource.Named("Test VM").blankInstance()
                )
            }
            creation.cancelAndJoin()
            queue.onViewModelInstanceInstantiated(requestID.captured, expected)

            verify(exactly = 1) {
                bridge.cppDeleteViewModelInstance(COMMAND_QUEUE_ADDR, any(), expected.handle)
            }
        }
    }

    test("View model instance deletion invokes native") {
        val queue = CommandQueue(renderContext, bridge)
        val requestID = slot<Long>()
        val instanceHandle = ViewModelInstanceHandle(CONFIRMED_VMI_HANDLE)
        every {
            bridge.cppDeleteViewModelInstance(
                COMMAND_QUEUE_ADDR,
                capture(requestID),
                instanceHandle.handle
            )
        } just runs

        queue.deleteViewModelInstance(instanceHandle)

        verify(exactly = 1) {
            bridge.cppDeleteViewModelInstance(
                COMMAND_QUEUE_ADDR,
                requestID.captured,
                instanceHandle.handle
            )
        }
    }

    test("Image creation waits for native success") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = ImageHandle(CONFIRMED_IMAGE_HANDLE)
            every {
                bridge.cppDecodeImage(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
            } returns expected.handle

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.decodeImage(FILE_BYTES)
            }

            creation.isCompleted.shouldBeFalse()
            queue.onImageDecoded(requestID.captured, expected)

            creation.await() shouldBe expected
            verify(exactly = 0) { bridge.cppDeleteImage(any(), any()) }
        }
    }

    test("Image creation reports native failure") {
        val queue = CommandQueue(renderContext, bridge)
        val requestID = slot<Long>()
        val errorMessage = "Invalid image bytes"
        every {
            bridge.cppDecodeImage(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
        } answers {
            queue.onImageError(requestID.captured, errorMessage)
            CONFIRMED_IMAGE_HANDLE
        }

        shouldThrow<RiveImageException> {
            queue.decodeImage(FILE_BYTES)
        }.message shouldContain errorMessage
    }

    test("Cancelled image creation deletes its provisional handle once") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = ImageHandle(CONFIRMED_IMAGE_HANDLE)
            every {
                bridge.cppDecodeImage(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
            } returns expected.handle
            every { bridge.cppDeleteImage(COMMAND_QUEUE_ADDR, expected.handle) } just runs

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.decodeImage(FILE_BYTES)
            }
            creation.cancelAndJoin()

            verify(exactly = 1) {
                bridge.cppDeleteImage(COMMAND_QUEUE_ADDR, expected.handle)
            }

            // A late success must not schedule a second deletion.
            queue.onImageDecoded(requestID.captured, expected)

            verify(exactly = 1) {
                bridge.cppDeleteImage(COMMAND_QUEUE_ADDR, expected.handle)
            }
        }
    }

    test("Image deletion invokes native") {
        val queue = CommandQueue(renderContext, bridge)
        val imageHandle = ImageHandle(CONFIRMED_IMAGE_HANDLE)
        every { bridge.cppDeleteImage(COMMAND_QUEUE_ADDR, imageHandle.handle) } just runs

        queue.deleteImage(imageHandle)

        verify(exactly = 1) {
            bridge.cppDeleteImage(COMMAND_QUEUE_ADDR, imageHandle.handle)
        }
    }

    test("Audio creation waits for native success") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = AudioHandle(CONFIRMED_AUDIO_HANDLE)
            every {
                bridge.cppDecodeAudio(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
            } returns expected.handle

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.decodeAudio(FILE_BYTES)
            }

            creation.isCompleted.shouldBeFalse()
            queue.onAudioDecoded(requestID.captured, expected)

            creation.await() shouldBe expected
            verify(exactly = 0) { bridge.cppDeleteAudio(any(), any()) }
        }
    }

    test("Audio creation reports native failure") {
        val queue = CommandQueue(renderContext, bridge)
        val requestID = slot<Long>()
        val errorMessage = "Invalid audio bytes"
        every {
            bridge.cppDecodeAudio(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
        } answers {
            queue.onAudioError(requestID.captured, errorMessage)
            CONFIRMED_AUDIO_HANDLE
        }

        shouldThrow<RiveAudioException> {
            queue.decodeAudio(FILE_BYTES)
        }.message shouldContain errorMessage
    }

    test("Cancelled audio creation deletes its provisional handle once") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = AudioHandle(CONFIRMED_AUDIO_HANDLE)
            every {
                bridge.cppDecodeAudio(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
            } returns expected.handle
            every { bridge.cppDeleteAudio(COMMAND_QUEUE_ADDR, expected.handle) } just runs

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.decodeAudio(FILE_BYTES)
            }
            creation.cancelAndJoin()

            verify(exactly = 1) {
                bridge.cppDeleteAudio(COMMAND_QUEUE_ADDR, expected.handle)
            }

            // A late success must not schedule a second deletion.
            queue.onAudioDecoded(requestID.captured, expected)

            verify(exactly = 1) {
                bridge.cppDeleteAudio(COMMAND_QUEUE_ADDR, expected.handle)
            }
        }
    }

    test("Audio deletion invokes native") {
        val queue = CommandQueue(renderContext, bridge)
        val audioHandle = AudioHandle(CONFIRMED_AUDIO_HANDLE)
        every { bridge.cppDeleteAudio(COMMAND_QUEUE_ADDR, audioHandle.handle) } just runs

        queue.deleteAudio(audioHandle)

        verify(exactly = 1) {
            bridge.cppDeleteAudio(COMMAND_QUEUE_ADDR, audioHandle.handle)
        }
    }

    test("Font creation waits for native success") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = FontHandle(CONFIRMED_FONT_HANDLE)
            every {
                bridge.cppDecodeFont(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
            } returns expected.handle

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.decodeFont(FILE_BYTES)
            }

            creation.isCompleted.shouldBeFalse()
            queue.onFontDecoded(requestID.captured, expected)

            creation.await() shouldBe expected
            verify(exactly = 0) { bridge.cppDeleteFont(any(), any()) }
        }
    }

    test("Font creation reports native failure") {
        val queue = CommandQueue(renderContext, bridge)
        val requestID = slot<Long>()
        val errorMessage = "Invalid font bytes"
        every {
            bridge.cppDecodeFont(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
        } answers {
            queue.onFontError(requestID.captured, errorMessage)
            CONFIRMED_FONT_HANDLE
        }

        shouldThrow<RiveFontException> {
            queue.decodeFont(FILE_BYTES)
        }.message shouldContain errorMessage
    }

    test("Cancelled font creation deletes its provisional handle once") {
        coroutineScope {
            val queue = CommandQueue(renderContext, bridge)
            val requestID = slot<Long>()
            val expected = FontHandle(CONFIRMED_FONT_HANDLE)
            every {
                bridge.cppDecodeFont(COMMAND_QUEUE_ADDR, capture(requestID), FILE_BYTES)
            } returns expected.handle
            every { bridge.cppDeleteFont(COMMAND_QUEUE_ADDR, expected.handle) } just runs

            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                queue.decodeFont(FILE_BYTES)
            }
            creation.cancelAndJoin()

            verify(exactly = 1) {
                bridge.cppDeleteFont(COMMAND_QUEUE_ADDR, expected.handle)
            }

            // A late success must not schedule a second deletion.
            queue.onFontDecoded(requestID.captured, expected)

            verify(exactly = 1) {
                bridge.cppDeleteFont(COMMAND_QUEUE_ADDR, expected.handle)
            }
        }
    }

    test("Font deletion invokes native") {
        val queue = CommandQueue(renderContext, bridge)
        val fontHandle = FontHandle(CONFIRMED_FONT_HANDLE)
        every { bridge.cppDeleteFont(COMMAND_QUEUE_ADDR, fontHandle.handle) } just runs

        queue.deleteFont(fontHandle)

        verify(exactly = 1) {
            bridge.cppDeleteFont(COMMAND_QUEUE_ADDR, fontHandle.handle)
        }
    }
})
