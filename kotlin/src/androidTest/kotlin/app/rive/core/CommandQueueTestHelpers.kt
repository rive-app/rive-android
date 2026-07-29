package app.rive.core

import androidx.annotation.RawRes
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.Artboard
import app.rive.Result
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.StateMachine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Continuously polls a [CommandQueue] on a background thread until closed.
 *
 * @param commandQueue The command queue whose callbacks should be delivered.
 */
internal class CommandQueuePoller(
    private val commandQueue: CommandQueue
) : AutoCloseable {
    private val keepPolling = AtomicBoolean(true)
    private val pollThread = thread(name = "RiveTestPoll") {
        while (keepPolling.get()) {
            commandQueue.pollMessages()
            Thread.sleep(1)
        }
    }

    /** Stops polling and waits for the background thread to finish. */
    override fun close() {
        keepPolling.set(false)
        pollThread.join(2_000)
    }
}

/**
 * Runs [block] while a temporary background thread continuously polls this command queue.
 *
 * Android instrumentation tests that enqueue command server work need polling even when the code
 * under test is not running inside the normal lifecycle-driven polling loop. Polling is stopped in
 * `finally` so failures inside [block] do not leak the helper thread.
 */
internal inline fun <T> CommandQueue.withPolling(block: CommandQueue.() -> T): T {
    val poller = CommandQueuePoller(this)

    return try {
        block()
    } finally {
        poller.close()
    }
}

/** Public app.rive resource wrapper set used by tests that exercise higher-level APIs. */
internal data class DefaultRiveResources(
    val file: RiveFile,
    val artboard: Artboard,
    val stateMachine: StateMachine,
) : AutoCloseable {
    /** Closes the state machine, artboard, and file while preserving any cleanup failures. */
    override fun close() {
        closeAll(stateMachine, artboard, file)
    }
}

/**
 * Loads [rawResourceId] and creates the default public [Artboard] and [StateMachine].
 *
 * The caller owns the returned resources and must close them.
 *
 * @param rawResourceId The raw Rive resource to load.
 * @return The loaded file and its default artboard and state machine.
 * @throws AssertionError If the file fails to load or produces an unexpected result.
 */
internal suspend fun RiveWorker.loadDefaultRiveResources(
    @RawRes rawResourceId: Int
): DefaultRiveResources {
    var file: RiveFile? = null
    var artboard: Artboard? = null
    var stateMachine: StateMachine? = null
    try {
        file = loadRiveFileOrFail(rawResourceId)
        artboard = Artboard.fromFile(file)
        stateMachine = StateMachine.fromArtboard(artboard)
        return DefaultRiveResources(file, artboard, stateMachine)
    } catch (failure: Throwable) {
        try {
            closeAll(stateMachine, artboard, file)
        } catch (closeFailure: Throwable) {
            failure.addSuppressed(closeFailure)
        }
        throw failure
    }
}

/**
 * Loads [rawResourceId] and creates the default public [Artboard] and [StateMachine].
 *
 * The returned resources are closed after [block] completes, keeping tests focused on the behavior
 * under test rather than nested resource cleanup.
 *
 * @param rawResourceId The raw Rive resource to load.
 * @param block The operation to run with the loaded resources.
 * @return The result of [block].
 * @throws AssertionError If the file fails to load or produces an unexpected result.
 */
internal suspend inline fun <T> RiveWorker.withDefaultRiveResources(
    @RawRes rawResourceId: Int,
    block: DefaultRiveResources.() -> T
): T {
    val resources = loadDefaultRiveResources(rawResourceId)
    try {
        return resources.block()
    } finally {
        resources.close()
    }
}

/** Verifies that final release started and completed command queue teardown. */
internal fun assertDisposed(commandQueue: CommandQueue) {
    assertTrue(
        commandQueue.isDisposed,
        "CommandQueue was not disposed after final release"
    )
    assertEquals(0, commandQueue.refCount)
    assertTrue(
        commandQueue.awaitShutdown(2_000),
        "CommandQueue native shutdown did not complete after final release"
    )
}

/**
 * Loads a raw Rive resource and fails the test if loading does not succeed.
 *
 * @param rawResourceId The raw Rive resource to load.
 * @return The loaded Rive file.
 * @throws AssertionError If the file fails to load or produces an unexpected result.
 */
internal suspend fun RiveWorker.loadRiveFileOrFail(@RawRes rawResourceId: Int): RiveFile {
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

/**
 * Closes every non-null resource, retaining later failures as suppressed exceptions.
 *
 * @param resources The resources to close in their supplied order.
 * @throws Throwable The first close failure, with any later failures attached.
 */
private fun closeAll(vararg resources: AutoCloseable?) {
    var failure: Throwable? = null
    resources.forEach { resource ->
        try {
            resource?.close()
        } catch (closeFailure: Throwable) {
            failure = failure?.apply {
                addSuppressed(closeFailure)
            } ?: closeFailure
        }
    }
    failure?.let { throw it }
}
