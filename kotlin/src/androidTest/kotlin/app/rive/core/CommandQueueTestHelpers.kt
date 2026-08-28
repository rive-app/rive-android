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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Continuously dispatches polling for one or more [CommandQueue] instances to the Android main
 * thread until closed.
 *
 * @param commandQueues The command queues whose callbacks should be delivered.
 */
internal class CommandQueuePoller(
    private val commandQueues: List<CommandQueue>
) : AutoCloseable {
    /**
     * Creates a poller for a single command queue.
     *
     * @param commandQueue The command queue whose callbacks should be delivered.
     */
    constructor(commandQueue: CommandQueue) : this(listOf(commandQueue))

    private val pollingStateLock = ReentrantLock()
    private val pollingStateChanged = pollingStateLock.newCondition()
    private var isRunning = true
    private var pauseDepth = 0
    private var isPollInProgress = false
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val pollThread = thread(name = "RiveTestPoll") {
        while (beginPoll()) {
            try {
                instrumentation.runOnMainSync {
                    commandQueues.forEach(CommandQueue::pollMessages)
                }
            } finally {
                finishPoll()
            }
            Thread.sleep(1)
        }
    }

    /**
     * Pauses polling and waits for any poll already in progress to finish.
     *
     * Polling resumes in `finally` after [block] finishes. Pause scopes may be nested.
     *
     * @param block The operation to run without native message polling.
     * @return The value produced by [block].
     * @throws IllegalStateException If this poller has already been closed.
     */
    internal fun <T> withPollingPaused(block: () -> T): T {
        pausePolling()
        return try {
            block()
        } finally {
            resumePolling()
        }
    }

    /** Stops polling and waits for the background thread to finish. */
    override fun close() {
        pollingStateLock.withLock {
            isRunning = false
            pollingStateChanged.signalAll()
        }
        pollThread.join(2_000)
    }

    /** Waits until polling is allowed and reserves the next poll operation. */
    private fun beginPoll(): Boolean = pollingStateLock.withLock {
        while (isRunning && pauseDepth > 0) {
            pollingStateChanged.await()
        }
        if (!isRunning) {
            return false
        }
        isPollInProgress = true
        true
    }

    /** Marks the current poll operation complete and wakes a thread waiting to pause. */
    private fun finishPoll() {
        pollingStateLock.withLock {
            isPollInProgress = false
            pollingStateChanged.signalAll()
        }
    }

    /** Pauses polling after any poll already in progress finishes. */
    private fun pausePolling() {
        pollingStateLock.withLock {
            check(isRunning) { "Cannot pause a closed command queue poller" }
            pauseDepth++
            while (isPollInProgress) {
                pollingStateChanged.await()
            }
        }
    }

    /** Resumes one nested polling pause. */
    private fun resumePolling() {
        pollingStateLock.withLock {
            check(pauseDepth > 0) { "Command queue polling is not paused" }
            pauseDepth--
            if (pauseDepth == 0) {
                pollingStateChanged.signalAll()
            }
        }
    }
}

/**
 * Runs [block] while a temporary background coordinator continuously dispatches command queue
 * polling to the Android main thread.
 *
 * Android instrumentation tests that enqueue command server work need polling even when the code
 * under test is not running inside the normal lifecycle-driven polling loop. Each poll runs on main
 * to preserve [CommandQueue] callback confinement. Polling is stopped in `finally` so failures
 * inside [block] do not leak the helper thread.
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
 * @throws RiveFileException If the file or its default artboard cannot be loaded.
 * @throws RiveArtboardException If the default state machine cannot be loaded.
 * @throws RiveResourceClosedException If this worker is disposed during creation.
 * @throws CancellationException If the coroutine is cancelled during creation.
 */
internal suspend fun RiveWorker.loadDefaultRiveResources(
    @RawRes rawResourceId: Int
): DefaultRiveResources {
    var file: RiveFile? = null
    var artboard: Artboard? = null
    var stateMachine: StateMachine? = null
    try {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        file = RiveFile.load(
            RiveFileSource.RawRes(rawResourceId, context.resources),
            this,
        )
        artboard = Artboard.create(file)
        stateMachine = StateMachine.create(artboard)
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
 * @throws RiveFileException If the file or its default artboard cannot be loaded.
 * @throws RiveArtboardException If the default state machine cannot be loaded.
 * @throws RiveResourceClosedException If this worker is disposed during creation.
 * @throws CancellationException If the coroutine is cancelled during creation.
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
