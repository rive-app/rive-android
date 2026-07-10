package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.ImageHandle
import app.rive.core.ViewModelInstanceHandle
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

private const val DIRTY_TIMEOUT_MS = 1_000L

class ViewModelInstanceUnitTest : FunSpec({
    viewModelInstanceDirtyMutations.forEach { mutation ->
        test("${mutation.name} emits a dirty event") {
            val subject = ViewModelInstanceDirtySubject()

            subject.expectDirtyEvent {
                mutation.mutate(subject)
            }
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
    private val fileHandle = FileHandle(1L)

    val instance = ViewModelInstance(
        ViewModelInstanceHandle(2L),
        worker,
        fileHandle,
    )
    val nestedInstance = ViewModelInstance(
        ViewModelInstanceHandle(3L),
        worker,
        fileHandle,
    )
    val image = ImageAsset(ImageHandle(4L), worker)
    val artboard = Artboard(
        ArtboardHandle(5L),
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
