package app.rive

import android.content.Context
import androidx.annotation.RawRes
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.core.CloseableRiveResources
import app.rive.core.CommandQueuePoller
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.core.loadRiveResources
import app.rive.runtime.kotlin.core.Rive
import org.junit.Rule
import org.junit.rules.ExternalResource

/**
 * Base class for androidTest cases that require the native Rive runtime to be initialized.
 *
 * Its outer lifecycle rule keeps the shared worker alive until all inner rules, including Compose
 * content disposal, have completed.
 *
 * @param autoPoll Whether to continuously poll the shared worker after it is first accessed.
 */
abstract class RiveAndroidTest(
    private val autoPoll: Boolean = true
) {
    protected val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var worker: RiveWorker? = null
    private var poller: CommandQueuePoller? = null
    private val managedResources = mutableListOf<CloseableRiveResources>()

    /**
     * Lazily-created worker for tests that only need one command queue.
     *
     * Polling starts on first access when [autoPoll] is true and continues through inner rule
     * teardown.
     */
    protected val riveWorker: RiveWorker
        get() = worker ?: createTestRiveWorker().also { activeWorker ->
            worker = activeWorker
            if (autoPoll) {
                poller = CommandQueuePoller(activeWorker)
            }
        }

    /**
     * Creates the worker owned by this test.
     *
     * Subclasses can override this to exercise worker configurations such as deferred rendering
     * while retaining the base class's polling and cleanup behavior.
     *
     * @return A worker configured for the test.
     */
    protected open fun createTestRiveWorker(): RiveWorker = RiveWorker()

    /**
     * Temporarily pauses automatic polling of [riveWorker].
     *
     * Polling resumes automatically after [block] finishes, including when it throws.
     *
     * @param block The operation to run without native message polling.
     * @return The value produced by [block].
     * @throws IllegalStateException If automatic polling is disabled or [riveWorker] has not been
     *    accessed yet.
     */
    protected fun <T> withRiveWorkerPollingPaused(block: () -> T): T =
        checkNotNull(poller) {
            "Automatic Rive worker polling is not active"
        }.withPollingPaused(block)

    /**
     * Loads selected Rive resources owned by this test's shared worker.
     *
     * Resources are registered for automatic cleanup before polling stops and the worker is
     * released. Callers may close them earlier because each resource's close operation is
     * idempotent.
     *
     * @param rawResourceId The raw Rive resource to load.
     * @param artboardName The artboard to create, or null for the default artboard.
     * @param stateMachineName The state machine to create, or null for the selected artboard's
     *    default.
     * @return The loaded file and its selected artboard and state machine.
     * @throws AssertionError If the file fails to load or produces an unexpected result.
     */
    internal suspend fun loadRiveResources(
        @RawRes rawResourceId: Int,
        artboardName: String? = null,
        stateMachineName: String? = null,
    ): CloseableRiveResources = riveWorker.loadRiveResources(
        rawResourceId,
        artboardName,
        stateMachineName,
    ).also(managedResources::add)

    /**
     * Loads default Rive resources owned by this test's shared worker.
     *
     * @param rawResourceId The raw Rive resource to load.
     * @return The loaded file and its default artboard and state machine.
     * @throws AssertionError If the file fails to load or produces an unexpected result.
     */
    internal suspend fun loadDefaultRiveResources(
        @RawRes rawResourceId: Int,
    ): CloseableRiveResources = loadRiveResources(rawResourceId)

    /**
     * Initializes Rive before other rules and releases the worker after their teardown.
     *
     * JUnit applies rules with higher order values inside rules with lower values. Using the
     * minimum value keeps this inherited lifecycle outside ordinary rules without requiring each
     * subclass to specify an order.
     */
    @get:Rule(order = Int.MIN_VALUE)
    val riveLifecycleRule = object : ExternalResource() {
        /** Initializes the native runtime before the test and its inner rules run. */
        override fun before() {
            Rive.init(context)
        }

        /** Releases test resources after every inner rule has completed teardown. */
        override fun after() {
            releaseRiveWorker()
        }
    }

    /** Closes managed resources, stops automatic polling, and releases the shared worker. */
    private fun releaseRiveWorker() {
        var failure: Throwable? = null
        // Attempt every teardown phase, retaining later failures without hiding the first one.
        val recordFailure = { teardownFailure: Throwable ->
            failure = failure?.apply {
                addSuppressed(teardownFailure)
            } ?: teardownFailure
        }

        managedResources.asReversed().forEach { resources ->
            try {
                resources.close()
            } catch (closeFailure: Throwable) {
                recordFailure(closeFailure)
            }
        }
        managedResources.clear()

        try {
            poller?.close()
        } catch (pollingFailure: Throwable) {
            recordFailure(pollingFailure)
        } finally {
            poller = null
        }

        try {
            worker?.let { activeWorker ->
                if (!activeWorker.isDisposed) {
                    activeWorker.release(javaClass.simpleName, "Test cleanup")
                }
                assertDisposed(activeWorker)
            }
        } catch (releaseFailure: Throwable) {
            recordFailure(releaseFailure)
        } finally {
            worker = null
        }

        failure?.let { throw it }
    }
}
