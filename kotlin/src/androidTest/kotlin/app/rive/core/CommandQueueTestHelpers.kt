package app.rive.core

import androidx.annotation.RawRes
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.Artboard
import app.rive.Result
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.StateMachine
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs [block] while a temporary background thread continuously polls this command queue.
 *
 * Android instrumentation tests that enqueue command server work need polling even when the code
 * under test is not running inside the normal lifecycle-driven polling loop. Polling is stopped in
 * `finally` so failures inside [block] do not leak the helper thread.
 */
internal inline fun <T> CommandQueue.withPolling(block: CommandQueue.() -> T): T {
    val keepPolling = AtomicBoolean(true)
    val pollThread = thread(name = "RiveTestPoll") {
        while (keepPolling.get()) {
            pollMessages()
            Thread.sleep(1)
        }
    }

    return try {
        block()
    } finally {
        keepPolling.set(false)
        pollThread.join(2000)
    }
}

/**
 * Loads [rawResourceId] and creates its default artboard/state machine while polling.
 *
 * File loading is suspend-based, but default artboard/state machine creation is queued work;
 * polling here ensures command server responses are delivered before the handles are used.
 */
internal fun CommandQueue.loadDefaultArtboardAndStateMachine(
    rawResourceId: Int
): Pair<ArtboardHandle, StateMachineHandle> {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return withPolling {
        val bytes = context.resources.openRawResource(rawResourceId)
            .use { it.readBytes() }
        val fileHandle = runBlocking { loadFile(bytes) }
        val artboardHandle = createDefaultArtboard(fileHandle)
        val stateMachineHandle = createDefaultStateMachine(artboardHandle)
        artboardHandle to stateMachineHandle
    }
}

/** Public app.rive resource wrapper set used by tests that exercise higher-level APIs. */
internal data class DefaultRiveResources(
    val file: RiveFile,
    val artboard: Artboard,
    val stateMachine: StateMachine,
) : AutoCloseable {
    override fun close() {
        stateMachine.close()
        artboard.close()
        file.close()
    }
}

/**
 * Loads [rawResourceId] and creates the default public [Artboard] and [StateMachine].
 *
 * The returned resources are closed after [block] completes, keeping tests focused on the behavior
 * under test rather than nested resource cleanup.
 */
internal suspend inline fun <T> RiveWorker.withDefaultRiveResources(
    @RawRes rawResourceId: Int,
    block: DefaultRiveResources.() -> T
): T {
    var file: RiveFile? = null
    var artboard: Artboard? = null
    var stateMachine: StateMachine? = null
    try {
        file = loadRiveFileOrFail(rawResourceId)
        artboard = Artboard.fromFile(file)
        stateMachine = StateMachine.fromArtboard(artboard)
        return DefaultRiveResources(file, artboard, stateMachine).block()
    } finally {
        stateMachine?.close()
        artboard?.close()
        file?.close()
    }
}

/** Verifies that final release synchronously completed command queue teardown. */
internal fun assertDisposed(commandQueue: CommandQueue) {
    assertTrue(
        commandQueue.isDisposed,
        "CommandQueue was not disposed after final release"
    )
    assertEquals(0, commandQueue.refCount)
}

private suspend fun RiveWorker.loadRiveFileOrFail(@RawRes rawResourceId: Int): RiveFile {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return when (
        val result = RiveFile.fromSource(
            RiveFileSource.RawRes(rawResourceId, context.resources),
            this
        )
    ) {
        is Result.Success -> result.value
        is Result.Error ->
            throw AssertionError("Failed to load Rive file: ${result.throwable.message}")

        is Result.Loading ->
            throw AssertionError("RiveFile.fromSource should not return Loading")
    }
}
