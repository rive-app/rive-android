@file:Suppress("DEPRECATION")

package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.ImageHandle
import app.rive.core.RivePropertyUpdate
import app.rive.core.ViewModelInstanceHandle
import app.rive.runtime.kotlin.core.ViewModel.PropertyDataType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

private const val DIRTY_TIMEOUT_MS = 1_000L
private const val TEST_FILE_HANDLE = 1L
private const val TEST_INSTANCE_HANDLE = 2L
private const val TEST_NESTED_INSTANCE_HANDLE = 3L
private const val TEST_IMAGE_HANDLE = 4L
private const val TEST_ARTBOARD_HANDLE = 5L

class ViewModelInstanceUnitTest : FunSpec({
    val fixture = installCommandQueueTestFixture()
    val renderContext = fixture.renderContextMock
    val bridge = fixture.commandQueueBridgeMock

    test("Factory returns an instance") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_FILE_HANDLE), worker)
        val source = ViewModelSource.Named("Test View Model").defaultInstance()
        val handle = ViewModelInstanceHandle(TEST_INSTANCE_HANDLE)
        coEvery {
            worker.createViewModelInstanceConfirmed(file.fileHandle, source)
        } returns handle

        val instance = ViewModelInstance.create(file, source)

        instance.instanceHandle shouldBe handle
        coVerify(exactly = 1) {
            worker.createViewModelInstanceConfirmed(file.fileHandle, source)
        }
    }

    test("Factory propagates creation failure") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_FILE_HANDLE), worker)
        val source = ViewModelSource.Named("Missing").blankInstance()
        val error = RiveFileException("Missing view model")
        coEvery {
            worker.createViewModelInstanceConfirmed(file.fileHandle, source)
        } throws error

        shouldThrow<RiveFileException> {
            ViewModelInstance.create(file, source)
        } shouldBe error
    }

    test("Factory propagates cancellation") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_FILE_HANDLE), worker)
        val source = ViewModelSource.Named("Test View Model").defaultInstance()
        val cancellation = CancellationException("Cancelled view model instance creation")
        coEvery {
            worker.createViewModelInstanceConfirmed(file.fileHandle, source)
        } throws cancellation

        shouldThrow<CancellationException> {
            ViewModelInstance.create(file, source)
        } shouldBe cancellation
    }

    test("Factory rejects a closed file before creating an instance") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val file = RiveFile(FileHandle(TEST_FILE_HANDLE), worker).also { it.close() }
        clearMocks(worker, answers = false, recordedCalls = true)

        shouldThrow<RiveResourceClosedException> {
            ViewModelInstance.fromFile(
                file,
                ViewModelSource.Named("Test").blankInstance(),
            )
        }.message shouldContain TEST_FILE_HANDLE.toString()

        confirmVerified(worker)
    }

    test("Creation validates every resource source before native dispatch") {
        val worker = CommandQueue(renderContext, bridge)
        val foreignWorker = CommandQueue(renderContext, bridge)
        val fileHandle = FileHandle(TEST_FILE_HANDLE)
        every { bridge.cppDeleteArtboard(any(), any(), any()) } just runs
        every { bridge.cppDeleteViewModelInstance(any(), any(), any()) } just runs
        val closedArtboard = Artboard(
            ArtboardHandle(TEST_ARTBOARD_HANDLE),
            worker,
            fileHandle,
            "Closed Artboard",
        ).also { it.close() }
        val siblingFileArtboard = Artboard(
            ArtboardHandle(TEST_ARTBOARD_HANDLE + 1),
            worker,
            FileHandle(TEST_FILE_HANDLE + 1),
            "Sibling File Artboard",
        )
        val closedParent = ViewModelInstance(
            ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
            worker,
            fileHandle,
        ).also { it.close() }
        val foreignParent = ViewModelInstance(
            ViewModelInstanceHandle(TEST_NESTED_INSTANCE_HANDLE),
            foreignWorker,
            fileHandle,
        )

        val closedArtboardSource = ViewModelSource.DefaultForArtboard(closedArtboard)
        listOf(
            closedArtboardSource.blankInstance(),
            closedArtboardSource.defaultInstance(),
            closedArtboardSource.namedInstance("Test Instance"),
            ViewModelInstanceSource.Reference(closedParent, "nested"),
            ViewModelInstanceSource.ReferenceListItem(closedParent, "list", 0),
        ).forEach { source ->
            shouldThrow<RiveResourceClosedException> {
                worker.createViewModelInstanceConfirmed(fileHandle, source)
            }
        }

        val siblingArtboardSource = ViewModelSource.DefaultForArtboard(siblingFileArtboard)
        listOf(
            siblingArtboardSource.blankInstance(),
            siblingArtboardSource.defaultInstance(),
            siblingArtboardSource.namedInstance("Test Instance"),
            ViewModelInstanceSource.Reference(foreignParent, "nested"),
            ViewModelInstanceSource.ReferenceListItem(foreignParent, "list", 0),
        ).forEach { source ->
            shouldThrow<RiveIncompatibleResourceException> {
                worker.createViewModelInstanceConfirmed(fileHandle, source)
            }
        }

        verify(exactly = 0) {
            bridge.cppDefaultVMCreateBlankVMI(any(), any(), any(), any())
            bridge.cppDefaultVMCreateDefaultVMI(any(), any(), any(), any())
            bridge.cppDefaultVMCreateNamedVMI(any(), any(), any(), any(), any())
            bridge.cppReferenceNestedVMI(any(), any(), any(), any())
            bridge.cppReferenceListItemVMI(any(), any(), any(), any(), any())
        }
    }

    test("All public operations throw after close without using the worker") {
        val subject = ViewModelInstanceDirtySubject()
        subject.instance.closed shouldBe false
        subject.instance.close()
        subject.instance.closed shouldBe true
        clearMocks(subject.worker, answers = false, recordedCalls = true)

        /** Verifies that [operation] rejects the closed test instance. */
        suspend fun expectClosed(operation: suspend () -> Unit) {
            shouldThrow<RiveResourceClosedException> {
                operation()
            }.message shouldContain TEST_INSTANCE_HANDLE.toString()
        }

        expectClosed { subject.instance.getViewModelName() }
        expectClosed { subject.instance.getName() }
        expectClosed { subject.instance.getNumberFlow("number") }
        expectClosed { subject.instance.getStringFlow("string") }
        expectClosed { subject.instance.getBooleanFlow("boolean") }
        expectClosed { subject.instance.getEnumFlow("enum") }
        expectClosed { subject.instance.getColorFlow("color") }
        expectClosed { subject.instance.getTriggerFlow("trigger") }
        expectClosed { subject.instance.setNumber("number", 1f) }
        expectClosed { subject.instance.setString("string", "value") }
        expectClosed { subject.instance.setBoolean("boolean", true) }
        expectClosed { subject.instance.setEnum("enum", "value") }
        expectClosed { subject.instance.setColor("color", 0xFF00FF00.toInt()) }
        expectClosed { subject.instance.fireTrigger("trigger") }
        expectClosed { subject.instance.setImage("image", subject.image) }
        expectClosed { subject.instance.setArtboard("artboard", subject.artboard) }
        expectClosed {
            subject.instance.setViewModelInstance("nested", subject.nestedInstance)
        }
        expectClosed { subject.instance.getListSize("list") }
        expectClosed {
            subject.instance.insertToListAtIndex("list", 0, subject.nestedInstance)
        }
        expectClosed { subject.instance.appendToList("list", subject.nestedInstance) }
        expectClosed { subject.instance.removeFromListAtIndex("list", 0) }
        expectClosed { subject.instance.removeFromList("list", subject.nestedInstance) }
        expectClosed { subject.instance.swapListItems("list", 0, 1) }

        confirmVerified(subject.worker)
    }

    test("Flows obtained while open reject first collection after close") {
        val subject = ViewModelInstanceDirtySubject()
        every { subject.worker.numberPropertyFlow } returns MutableSharedFlow()
        every { subject.worker.triggerPropertyFlow } returns MutableSharedFlow()
        val numberFlow = subject.instance.getNumberFlow("number")
        val triggerFlow = subject.instance.getTriggerFlow("trigger")
        subject.instance.close()
        clearMocks(subject.worker, answers = false, recordedCalls = true)

        shouldThrow<RiveResourceClosedException> {
            numberFlow.first()
        }.message shouldContain TEST_INSTANCE_HANDLE.toString()
        shouldThrow<RiveResourceClosedException> {
            triggerFlow.first()
        }.message shouldContain TEST_INSTANCE_HANDLE.toString()

        confirmVerified(subject.worker)
    }

    test("Value collection completes when close wins before subscription acquisition") {
        val subject = ViewModelInstanceFlowSubject()
        val updates = BlockingSharedFlow<RivePropertyUpdate<Float>>()
        every { subject.worker.numberPropertyFlow } returns updates
        val propertyFlow = subject.instance.getNumberFlow("number")

        coroutineScope {
            val collector = launch(Dispatchers.Default) {
                propertyFlow.collect()
            }
            updates.awaitCollection()
            try {
                subject.instance.close()
            } finally {
                updates.resumeCollection()
            }
            withTimeout(DIRTY_TIMEOUT_MS) {
                collector.join()
            }
        }

        verify(exactly = 0) {
            subject.worker.subscribeToProperty(any(), any(), any())
        }
        coVerify(exactly = 0) {
            subject.worker.getNumberProperty(any(), any())
        }
    }

    test("Trigger collection completes when close wins before subscription acquisition") {
        val subject = ViewModelInstanceFlowSubject()
        val updates = BlockingSharedFlow<RivePropertyUpdate<Unit>>()
        every { subject.worker.triggerPropertyFlow } returns updates
        val triggerFlow = subject.instance.getTriggerFlow("trigger")

        coroutineScope {
            val collector = launch(Dispatchers.Default) {
                triggerFlow.collect()
            }
            updates.awaitCollection()
            try {
                subject.instance.close()
            } finally {
                updates.resumeCollection()
            }
            withTimeout(DIRTY_TIMEOUT_MS) {
                collector.join()
            }
        }

        verify(exactly = 0) {
            subject.worker.subscribeToProperty(any(), any(), any())
        }
    }

    test("Collectors share one native property subscription") {
        val subject = ViewModelInstanceFlowSubject()
        val propertyFlow = subject.instance.getNumberFlow("number")
        val getterCount = AtomicInteger()
        val bothCollectorsStarted = CompletableDeferred<Unit>()
        coEvery {
            subject.worker.getNumberProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
            )
        } answers {
            if (getterCount.incrementAndGet() == 2) {
                bothCollectorsStarted.complete(Unit)
            }
            0f
        }

        coroutineScope {
            val firstCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                propertyFlow.collect()
            }
            val secondCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                propertyFlow.collect()
            }
            withTimeout(DIRTY_TIMEOUT_MS) {
                bothCollectorsStarted.await()
            }

            verify(exactly = 1) {
                subject.worker.subscribeToProperty(
                    ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                    "number",
                    PropertyDataType.NUMBER,
                )
            }
            coVerify(exactly = 2) {
                subject.worker.getNumberProperty(
                    ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                    "number",
                )
            }

            firstCollector.cancelAndJoin()
            verify(exactly = 0) {
                subject.worker.unsubscribeFromProperty(any(), any(), any())
            }

            secondCollector.cancelAndJoin()
            verify(exactly = 1) {
                subject.worker.unsubscribeFromProperty(
                    ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                    "number",
                    PropertyDataType.NUMBER,
                )
            }
        }
    }

    test("Trigger collectors share one native property subscription") {
        val subject = ViewModelInstanceFlowSubject()
        val triggerFlow = subject.instance.getTriggerFlow("trigger")
        val firstReceived = CompletableDeferred<Unit>()
        val secondReceived = CompletableDeferred<Unit>()

        coroutineScope {
            val firstCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                triggerFlow.collect { firstReceived.complete(Unit) }
            }
            val secondCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                triggerFlow.collect { secondReceived.complete(Unit) }
            }
            withTimeout(DIRTY_TIMEOUT_MS) {
                subject.triggerUpdates.subscriptionCount.first { it == 2 }
                subject.triggerUpdates.emit(
                    CommandQueue.PropertyUpdate(
                        ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                        "trigger",
                        Unit,
                    )
                )
                firstReceived.await()
                secondReceived.await()
            }

            verify(exactly = 1) {
                subject.worker.subscribeToProperty(
                    ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                    "trigger",
                    PropertyDataType.TRIGGER,
                )
            }

            firstCollector.cancelAndJoin()
            verify(exactly = 0) {
                subject.worker.unsubscribeFromProperty(any(), any(), any())
            }

            secondCollector.cancelAndJoin()
            verify(exactly = 1) {
                subject.worker.unsubscribeFromProperty(
                    ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                    "trigger",
                    PropertyDataType.TRIGGER,
                )
            }
        }
    }

    test("Property flow relays updates and unsubscribes after collection") {
        val subject = ViewModelInstanceFlowSubject()
        val subscribed = CompletableDeferred<Unit>()
        every {
            subject.worker.subscribeToProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        } answers { subscribed.complete(Unit) }

        val value = async(start = CoroutineStart.UNDISPATCHED) {
            subject.instance.getNumberFlow("number").first()
        }
        withTimeout(DIRTY_TIMEOUT_MS) { subscribed.await() }

        subject.numberUpdates.emit(
            CommandQueue.PropertyUpdate(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                42f,
            )
        )

        val actualValue = withTimeout(DIRTY_TIMEOUT_MS) {
            value.await()
        }
        actualValue shouldBe 42f
        verify(exactly = 1) {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        }
    }

    test("Cancelling a collector ignores a disposed worker during unsubscribe") {
        val subject = ViewModelInstanceFlowSubject()
        val subscribed = CompletableDeferred<Unit>()
        every {
            subject.worker.subscribeToProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        } answers { subscribed.complete(Unit) }
        every {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        } throws RiveResourceClosedException("RiveWorker is disposed")

        coroutineScope {
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                subject.instance.getNumberFlow("number").collect()
            }
            withTimeout(DIRTY_TIMEOUT_MS) { subscribed.await() }

            collector.cancelAndJoin()
        }

        verify(exactly = 1) {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        }
    }

    test("Cancelling the initial property request releases the native subscription") {
        val subject = ViewModelInstanceFlowSubject()
        val getterStarted = CompletableDeferred<Unit>()
        val getterMayComplete = CompletableDeferred<Float>()
        coEvery {
            subject.worker.getNumberProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
            )
        } coAnswers {
            getterStarted.complete(Unit)
            getterMayComplete.await()
        }

        coroutineScope {
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                subject.instance.getNumberFlow("number").collect()
            }
            withTimeout(DIRTY_TIMEOUT_MS) { getterStarted.await() }

            collector.cancelAndJoin()
        }

        verify(exactly = 1) {
            subject.worker.subscribeToProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        }
        verify(exactly = 1) {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        }
    }

    test("Failed initial property request releases native subscription") {
        val subject = ViewModelInstanceFlowSubject()
        val expectedFailure = RiveViewModelInstanceException("Missing number")
        coEvery {
            subject.worker.getNumberProperty(any(), "number")
        } throws expectedFailure

        shouldThrow<RiveViewModelInstanceException> {
            subject.instance.getNumberFlow("number").first()
        }

        verify(exactly = 1) {
            subject.worker.subscribeToProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        }
        verify(exactly = 1) {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        }
    }

    test("Close during initial property request completes collection normally") {
        val subject = ViewModelInstanceFlowSubject()
        coEvery {
            subject.worker.getNumberProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
            )
        } coAnswers {
            subject.instance.close()
            throw RiveViewModelInstanceException("View model instance was deleted")
        }

        withTimeout(DIRTY_TIMEOUT_MS) {
            subject.instance.getNumberFlow("number").collect()
        }

        verify(exactly = 1) {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        }
        verify(exactly = 1) {
            subject.worker.deleteViewModelInstance(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE)
            )
        }
    }

    test("Close completes active collectors and removes native subscriptions") {
        val subject = ViewModelInstanceFlowSubject()
        val numberFlow = subject.instance.getNumberFlow("number")
        val triggerFlow = subject.instance.getTriggerFlow("trigger")
        val numberSubscribed = CompletableDeferred<Unit>()
        val triggerSubscribed = CompletableDeferred<Unit>()
        every {
            subject.worker.subscribeToProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        } answers { numberSubscribed.complete(Unit) }
        every {
            subject.worker.subscribeToProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "trigger",
                PropertyDataType.TRIGGER,
            )
        } answers { triggerSubscribed.complete(Unit) }

        coroutineScope {
            val numberCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                numberFlow.collect()
            }
            val triggerCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                triggerFlow.collect()
            }
            withTimeout(DIRTY_TIMEOUT_MS) {
                numberSubscribed.await()
                triggerSubscribed.await()
            }

            subject.instance.close()

            withTimeout(DIRTY_TIMEOUT_MS) {
                numberCollector.join()
                triggerCollector.join()
            }
        }

        verify(exactly = 1) {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        }
        verify(exactly = 1) {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "trigger",
                PropertyDataType.TRIGGER,
            )
        }
        verify(exactly = 1) {
            subject.worker.deleteViewModelInstance(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE)
            )
        }
        verifyOrder {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
            subject.worker.deleteViewModelInstance(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE)
            )
        }
        verifyOrder {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "trigger",
                PropertyDataType.TRIGGER,
            )
            subject.worker.deleteViewModelInstance(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE)
            )
        }
    }

    test("Closing after the last collector stops does not unsubscribe twice") {
        val subject = ViewModelInstanceFlowSubject()
        val subscribed = CompletableDeferred<Unit>()
        every {
            subject.worker.subscribeToProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        } answers { subscribed.complete(Unit) }

        coroutineScope {
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                subject.instance.getNumberFlow("number").collect()
            }
            withTimeout(DIRTY_TIMEOUT_MS) { subscribed.await() }

            collector.cancelAndJoin()
            subject.instance.close()
        }

        verify(exactly = 1) {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
        }
        verify(exactly = 1) {
            subject.worker.deleteViewModelInstance(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE)
            )
        }
        verifyOrder {
            subject.worker.unsubscribeFromProperty(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
                "number",
                PropertyDataType.NUMBER,
            )
            subject.worker.deleteViewModelInstance(
                ViewModelInstanceHandle(TEST_INSTANCE_HANDLE)
            )
        }
    }

    test("Resource arguments are checked before property mutations") {
        val subject = ViewModelInstanceDirtySubject()
        subject.image.close()
        subject.artboard.close()
        subject.nestedInstance.close()
        clearMocks(subject.worker, answers = false, recordedCalls = true)

        shouldThrow<RiveResourceClosedException> {
            subject.instance.setImage("image", subject.image)
        }.message shouldContain TEST_IMAGE_HANDLE.toString()
        shouldThrow<RiveResourceClosedException> {
            subject.instance.setArtboard("artboard", subject.artboard)
        }.message shouldContain TEST_ARTBOARD_HANDLE.toString()
        listOf<(ViewModelInstance) -> Unit>(
            { it.setViewModelInstance("nested", subject.nestedInstance) },
            { it.insertToListAtIndex("list", 0, subject.nestedInstance) },
            { it.appendToList("list", subject.nestedInstance) },
            { it.removeFromList("list", subject.nestedInstance) },
        ).forEach { mutation ->
            shouldThrow<RiveResourceClosedException> {
                mutation(subject.instance)
            }.message shouldContain TEST_NESTED_INSTANCE_HANDLE.toString()
        }

        confirmVerified(subject.worker)
    }

    test("Resource property mutations reject resources from another worker") {
        val subject = ViewModelInstanceDirtySubject()
        val foreignWorker = mockk<CommandQueue>(relaxed = true)
        val foreignImage = ImageAsset(ImageHandle(TEST_IMAGE_HANDLE + 10), foreignWorker)
        val foreignArtboard = Artboard(
            ArtboardHandle(TEST_ARTBOARD_HANDLE + 10),
            foreignWorker,
            FileHandle(TEST_FILE_HANDLE),
            "Foreign Artboard",
        )
        val foreignInstance = ViewModelInstance(
            ViewModelInstanceHandle(TEST_NESTED_INSTANCE_HANDLE + 10),
            foreignWorker,
            FileHandle(TEST_FILE_HANDLE),
        )
        clearMocks(subject.worker, foreignWorker, answers = false, recordedCalls = true)

        shouldThrow<RiveIncompatibleResourceException> {
            subject.instance.setImage("image", foreignImage)
        }.message shouldContain (TEST_IMAGE_HANDLE + 10).toString()
        shouldThrow<RiveIncompatibleResourceException> {
            subject.instance.setArtboard("artboard", foreignArtboard)
        }.message shouldContain (TEST_ARTBOARD_HANDLE + 10).toString()
        listOf<(ViewModelInstance) -> Unit>(
            { it.setViewModelInstance("nested", foreignInstance) },
            { it.insertToListAtIndex("list", 0, foreignInstance) },
            { it.appendToList("list", foreignInstance) },
            { it.removeFromList("list", foreignInstance) },
        ).forEach { mutation ->
            shouldThrow<RiveIncompatibleResourceException> {
                mutation(subject.instance)
            }.message shouldContain (TEST_NESTED_INSTANCE_HANDLE + 10).toString()
        }

        confirmVerified(subject.worker)
        confirmVerified(foreignWorker)
    }

    test("Resource property mutations check openness before compatibility") {
        val subject = ViewModelInstanceDirtySubject()
        val foreignWorker = mockk<CommandQueue>(relaxed = true)
        val foreignImage = ImageAsset(ImageHandle(TEST_IMAGE_HANDLE + 10), foreignWorker)
            .also { it.close() }
        clearMocks(subject.worker, foreignWorker, answers = false, recordedCalls = true)

        shouldThrow<RiveResourceClosedException> {
            subject.instance.setImage("image", foreignImage)
        }.message shouldContain (TEST_IMAGE_HANDLE + 10).toString()

        confirmVerified(subject.worker)
        confirmVerified(foreignWorker)
    }

    viewModelInstanceDirtyMutations.forEach { mutation ->
        test("${mutation.name} emits a dirty event") {
            val subject = ViewModelInstanceDirtySubject()

            subject.expectDirtyEvent {
                mutation.mutate(subject)
            }
        }
    }

    test("setImage with null clears the image property") {
        val subject = ViewModelInstanceDirtySubject()

        subject.instance.setImage("image", null)

        verify(exactly = 1) {
            subject.worker.setImageProperty(ViewModelInstanceHandle(2L), "image", null)
        }
    }
})

private data class ViewModelInstanceDirtyMutation(
    val name: String,
    val mutate: (ViewModelInstanceDirtySubject) -> Unit,
)

private val viewModelInstanceDirtyMutations = listOf(
    ViewModelInstanceDirtyMutation("setNumber") { subject ->
        subject.instance.setNumber("number", 1f)
    },
    ViewModelInstanceDirtyMutation("setString") { subject ->
        subject.instance.setString("string", "value")
    },
    ViewModelInstanceDirtyMutation("setBoolean") { subject ->
        subject.instance.setBoolean("boolean", true)
    },
    ViewModelInstanceDirtyMutation("setEnum") { subject ->
        subject.instance.setEnum("enum", "value")
    },
    ViewModelInstanceDirtyMutation("setColor") { subject ->
        subject.instance.setColor("color", 0xFF00FF00.toInt())
    },
    ViewModelInstanceDirtyMutation("fireTrigger") { subject ->
        subject.instance.fireTrigger("trigger")
    },
    ViewModelInstanceDirtyMutation("setImage") { subject ->
        subject.instance.setImage("image", subject.image)
    },
    ViewModelInstanceDirtyMutation("clearImage") { subject ->
        subject.instance.setImage("image", null)
    },
    ViewModelInstanceDirtyMutation("setArtboard") { subject ->
        subject.instance.setArtboard("artboard", subject.artboard)
    },
    ViewModelInstanceDirtyMutation("clearArtboard") { subject ->
        subject.instance.setArtboard("artboard", null)
    },
    ViewModelInstanceDirtyMutation("setViewModelInstance") { subject ->
        subject.instance.setViewModelInstance("nested", subject.nestedInstance)
    },
    ViewModelInstanceDirtyMutation("insertToListAtIndex") { subject ->
        subject.instance.insertToListAtIndex("list", 0, subject.nestedInstance)
    },
    ViewModelInstanceDirtyMutation("appendToList") { subject ->
        subject.instance.appendToList("list", subject.nestedInstance)
    },
    ViewModelInstanceDirtyMutation("removeFromListAtIndex") { subject ->
        subject.instance.removeFromListAtIndex("list", 0)
    },
    ViewModelInstanceDirtyMutation("removeFromList") { subject ->
        subject.instance.removeFromList("list", subject.nestedInstance)
    },
    ViewModelInstanceDirtyMutation("swapListItems") { subject ->
        subject.instance.swapListItems("list", 0, 1)
    },
)

private class ViewModelInstanceDirtySubject {
    val worker = mockk<CommandQueue>(relaxed = true)
    private val fileHandle = FileHandle(TEST_FILE_HANDLE)

    val instance = ViewModelInstance(
        ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
        worker,
        fileHandle,
    )
    val nestedInstance = ViewModelInstance(
        ViewModelInstanceHandle(TEST_NESTED_INSTANCE_HANDLE),
        worker,
        fileHandle,
    )
    val image = ImageAsset(ImageHandle(TEST_IMAGE_HANDLE), worker)
    val artboard = Artboard(
        ArtboardHandle(TEST_ARTBOARD_HANDLE),
        worker,
        fileHandle,
        "Dirty Test Artboard",
    )

    /**
     * Runs [mutate] and waits for a new dirty event from [instance].
     *
     * @param mutate The mutation expected to dirty [instance].
     * @throws kotlinx.coroutines.TimeoutCancellationException If [mutate] does not emit dirty.
     */
    suspend fun expectDirtyEvent(mutate: () -> Unit) = coroutineScope {
        val dirtyEvent = async(start = CoroutineStart.UNDISPATCHED) {
            val replayedDirtyEvents = instance.dirtyFlow.replayCache.size
            instance.dirtyFlow.drop(replayedDirtyEvents).first()
        }

        mutate()

        withTimeout(DIRTY_TIMEOUT_MS) {
            dirtyEvent.await()
        }
    }
}

private class ViewModelInstanceFlowSubject {
    val worker = mockk<CommandQueue>(relaxed = true)
    val numberUpdates = MutableSharedFlow<RivePropertyUpdate<Float>>()
    val triggerUpdates = MutableSharedFlow<RivePropertyUpdate<Unit>>()

    val instance = ViewModelInstance(
        ViewModelInstanceHandle(TEST_INSTANCE_HANDLE),
        worker,
        FileHandle(TEST_FILE_HANDLE),
    )

    init {
        every { worker.numberPropertyFlow } returns numberUpdates
        every { worker.triggerPropertyFlow } returns triggerUpdates
    }
}

/**
 * Pauses collection before registering with the backing shared flow so a test can close the view
 * model instance after collection begins but before its property subscription is acquired.
 */
private class BlockingSharedFlow<T> : SharedFlow<T> {
    private val delegate = MutableSharedFlow<T>()
    private val collectionEntered = CountDownLatch(1)
    private val mayCollect = CountDownLatch(1)

    override val replayCache: List<T>
        get() = delegate.replayCache

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        collectionEntered.countDown()
        check(mayCollect.await(DIRTY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            "Timed out waiting to resume SharedFlow collection"
        }
        delegate.collect(collector)
    }

    /** Waits until a collector has reached the pause before shared-flow registration. */
    fun awaitCollection() {
        check(collectionEntered.await(DIRTY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            "Timed out waiting for SharedFlow collection"
        }
    }

    /** Allows the paused collector to register with the backing shared flow. */
    fun resumeCollection() {
        mayCollect.countDown()
    }
}
