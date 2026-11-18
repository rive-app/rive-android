package app.rive.testutil

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/**
 * Runs effect-only Compose content on a JVM test scheduler, without an Android UI host.
 *
 * Owns the composition, recomposer, frame clock, and effect scope. Call [close] in a finally block
 * (or use [use]) before clearing mocks. Call [installTraceMock] in the test spec to stub Android tracing.
 * This does not emulate layout, drawing, or Android lifecycle events.
 *
 * @param testScope The scope whose scheduler drives recomposition and effects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RuntimeComposition(private val testScope: TestScope) : AutoCloseable {
    private val failures = mutableListOf<Throwable>()
    private val frameClock = BroadcastFrameClock()
    private var frameNanos = 0L
    private val scope = CoroutineScope(
        SupervisorJob() + StandardTestDispatcher(testScope.testScheduler) + frameClock +
            CoroutineExceptionHandler { _, error -> failures.add(error) },
    )
    private val recomposer = Recomposer(scope.coroutineContext)
    private val composition = Composition(EffectOnlyApplier(), recomposer)

    init {
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    /** Sets content that may remember values and run effects, but must not emit UI nodes. */
    fun setContent(content: @Composable () -> Unit) = composition.setContent(content)

    /**
     * Drains snapshot notifications, recomposition, and effects runnable at the current test time.
     * Supplies requested frames with monotonically increasing timestamps, without advancing delayed
     * coroutine work or waiting for external responses. Two idle passes allow effects' state writes
     * to reach the recomposer before returning.
     *
     * @param maxPasses Bounds cascading work, including effects that continuously request frames.
     * @throws IllegalStateException If the composition does not settle within [maxPasses].
     */
    fun runUntilIdle(maxPasses: Int = 100) {
        require(maxPasses >= 2) { "At least two passes are required to establish idle" }
        var wasIdle = false
        repeat(maxPasses) {
            testScope.runCurrent()
            Snapshot.sendApplyNotifications()
            testScope.runCurrent()
            checkFailures()
            val idle = !recomposer.hasPendingWork && !frameClock.hasAwaiters
            if (idle && wasIdle) return
            wasIdle = idle
            if (frameClock.hasAwaiters) {
                frameNanos =
                    maxOf(
                        frameNanos + 16_666_667L,
                        testScope.testScheduler.currentTime * 1_000_000L
                    )
                frameClock.sendFrame(frameNanos)
            }
        }
        error("Composition did not become idle after $maxPasses passes")
    }

    /** Cancels effects before disposal, to exercise host shutdown ordering. */
    fun cancelRecomposer() = recomposer.cancel()

    /** Disposes remembered values and drains immediate cleanup without cancelling the recomposer. */
    fun dispose() {
        composition.dispose()
        testScope.runCurrent()
        checkFailures()
    }

    /** Disposes content and cancels all owned runtime work, including after a failed test. */
    override fun close() {
        try {
            composition.dispose()
        } finally {
            recomposer.cancel()
            scope.cancel()
            testScope.runCurrent()
        }
        checkFailures()
    }

    /** Surfaces asynchronous effect failures on the test thread. */
    private fun checkFailures() {
        failures.firstOrNull()?.let { throw AssertionError("Compose runtime work failed", it) }
    }
}

/**
 * An applier for tests of remembered values and effects, which do not build a node tree.
 * Rejects node operations so accidentally adding UI content cannot silently pass a runtime test.
 */
internal class EffectOnlyApplier : AbstractApplier<Unit>(Unit) {
    /** Rejects insertion because this composition has no node tree. */
    override fun insertTopDown(index: Int, instance: Unit): Unit =
        error("Node insertion is unsupported")

    /** Rejects insertion because this composition has no node tree. */
    override fun insertBottomUp(index: Int, instance: Unit): Unit =
        error("Node insertion is unsupported")

    /** Rejects removal because this composition has no node tree. */
    override fun remove(index: Int, count: Int): Unit = error("Node removal is unsupported")

    /** Rejects movement because this composition has no node tree. */
    override fun move(from: Int, to: Int, count: Int): Unit = error("Node movement is unsupported")

    /** Clearing is valid during disposal even though no nodes were retained. */
    override fun onClear() = Unit
}
