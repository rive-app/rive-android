package app.rive.core

import androidx.annotation.RawRes
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.Artboard
import app.rive.RiveArtboardException
import app.rive.RiveFile
import app.rive.RiveFileException
import app.rive.RiveFileSource
import app.rive.RiveResourceClosedException
import app.rive.StateMachine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Defers automatic delivery while tests inspect intermediate UI state.
 *
 * @param block Work to perform before queued replies may be delivered.
 */
internal fun <T> List<CommandQueue>.withMessageDeliveryPaused(block: () -> T): T {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync { forEach { it.setMessageDeliveryPaused(true) } }
    return try {
        block()
    } finally {
        instrumentation.runOnMainSync { forEach { it.setMessageDeliveryPaused(false) } }
    }
}

/** Closeable public app.rive resource set used by higher-level instrumentation tests. */
internal data class CloseableRiveResources(
    val file: RiveFile,
    val artboard: Artboard,
    val stateMachine: StateMachine,
) : AutoCloseable {
    /** Closes the state machine, artboard, and file while preserving any cleanup failures. */
    override fun close() {
        closeAll(stateMachine, artboard, file)
    }
}

/** Compatibility name for callers that specifically request default resources. */
internal typealias DefaultRiveResources = CloseableRiveResources

/**
 * Loads [rawResourceId] and creates its selected public [Artboard] and [StateMachine].
 *
 * The caller owns the returned resources and must close them.
 *
 * @param rawResourceId The raw Rive resource to load.
 * @param artboardName The artboard to create, or null for the default artboard.
 * @param stateMachineName The state machine to create, or null for the selected artboard's default.
 * @return The loaded file and its selected artboard and state machine.
 * @throws RiveFileException If the file or requested artboard cannot be loaded.
 * @throws RiveArtboardException If the requested state machine cannot be loaded.
 * @throws RiveResourceClosedException If this worker is disposed during creation.
 * @throws CancellationException If the coroutine is cancelled during creation.
 */
internal suspend fun RiveWorker.loadRiveResources(
    @RawRes rawResourceId: Int,
    artboardName: String? = null,
    stateMachineName: String? = null,
): CloseableRiveResources {
    var file: RiveFile? = null
    var artboard: Artboard? = null
    var stateMachine: StateMachine? = null
    try {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        file = RiveFile.load(
            RiveFileSource.RawRes(rawResourceId, context.resources),
            this,
        )
        artboard = Artboard.create(file, artboardName)
        stateMachine = StateMachine.create(artboard, stateMachineName)
        return CloseableRiveResources(file, artboard, stateMachine)
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
 * Loads [rawResourceId] and creates its default public [Artboard] and [StateMachine].
 *
 * @param rawResourceId The raw Rive resource to load.
 * @return The loaded file and its default artboard and state machine.
 * @throws RiveFileException If the file or its default artboard cannot be loaded.
 * @throws RiveArtboardException If the default state machine cannot be loaded.
 * @throws RiveResourceClosedException If this worker is disposed during creation.
 * @throws CancellationException If the coroutine is cancelled during creation.
 */
internal suspend fun RiveWorker.loadDefaultRiveResources(
    @RawRes rawResourceId: Int,
): DefaultRiveResources = loadRiveResources(rawResourceId)

/**
 * Loads [rawResourceId] and creates its selected public [Artboard] and [StateMachine].
 *
 * The returned resources are closed after [block] completes, keeping tests focused on the behavior
 * under test rather than nested resource cleanup.
 *
 * @param rawResourceId The raw Rive resource to load.
 * @param artboardName The artboard to create, or null for the default artboard.
 * @param stateMachineName The state machine to create, or null for the selected artboard's default.
 * @param block The operation to run with the loaded resources.
 * @return The result of [block].
 * @throws RiveFileException If the file or requested artboard cannot be loaded.
 * @throws RiveArtboardException If the requested state machine cannot be loaded.
 * @throws RiveResourceClosedException If this worker is disposed during creation.
 * @throws CancellationException If the coroutine is cancelled during creation.
 */
internal suspend inline fun <T> RiveWorker.withRiveResources(
    @RawRes rawResourceId: Int,
    artboardName: String? = null,
    stateMachineName: String? = null,
    block: CloseableRiveResources.() -> T,
): T {
    val resources = loadRiveResources(rawResourceId, artboardName, stateMachineName)
    try {
        return resources.block()
    } finally {
        resources.close()
    }
}

/**
 * Runs [block] with the default resources created from [rawResourceId], then closes them.
 *
 * @param rawResourceId The raw Rive resource to load.
 * @param block The operation to run with the loaded resources.
 * @return The result of [block].
 * @throws RiveFileException If the file or its default artboard cannot be loaded.
 * @throws RiveArtboardException If the default state machine cannot be loaded.
 * @throws RiveResourceClosedException If this worker is disposed during creation.
 * @throws CancellationException If the coroutine is cancelled during creation.
 */
internal suspend inline fun <T> RiveWorker.withDefaultRiveResources(
    @RawRes rawResourceId: Int,
    block: DefaultRiveResources.() -> T,
): T = withRiveResources(rawResourceId, block = block)

/** Verifies that final release started and completed command queue teardown. */
internal fun assertDisposed(commandQueue: CommandQueue) {
    // A reply can resume the test before Main releases the drain's temporary worker reference.
    // Wait for teardown before asserting the terminal state, rather than racing that reference.
    assertTrue(
        commandQueue.awaitShutdown(2_000),
        "CommandQueue native shutdown did not complete after final release"
    )
    assertTrue(commandQueue.isDisposed, "CommandQueue was not disposed after final release")
    assertEquals(0, commandQueue.refCount)
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
