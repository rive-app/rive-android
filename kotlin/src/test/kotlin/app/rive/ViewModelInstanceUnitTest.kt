@file:Suppress("DEPRECATION")

package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.ImageHandle
import app.rive.core.ViewModelInstanceHandle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

private const val DIRTY_TIMEOUT_MS = 1_000L
private const val TEST_FILE_HANDLE = 1L
private const val TEST_INSTANCE_HANDLE = 2L
private const val TEST_NESTED_INSTANCE_HANDLE = 3L
private const val TEST_IMAGE_HANDLE = 4L
private const val TEST_ARTBOARD_HANDLE = 5L

class ViewModelInstanceUnitTest : FunSpec({
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

    test("All public operations throw after close without using the worker") {
        val subject = ViewModelInstanceDirtySubject()
        subject.instance.close()
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
