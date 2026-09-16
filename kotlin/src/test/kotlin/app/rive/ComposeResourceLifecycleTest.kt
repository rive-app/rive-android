package app.rive

import android.os.Trace
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.StateMachineHandle
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Tests Compose resource lifetimes on the JVM using a real Composition and Recomposer.
 *
 * Runs rememberRiveFile and rememberStateMachineResult with real worker reference counting.
 * Native calls and Android tracing are mocked; no Activity, ComposeView, or rendering is involved.
 * Explicit recomposer cancellation isolates shutdown ordering independently of an Android host.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeResourceLifecycleTest : FunSpec({
    val fixture = installCommandQueueTestFixture()
    beforeTest {
        mockkStatic(Trace::class)
        every { Trace.beginSection(any()) } just runs
        every { Trace.endSection() } just runs
    }
    afterTest { unmockkStatic(Trace::class) }

    for (cancelRecomposer in listOf(false, true)) {
        for (useKey in listOf(false, true)) {
            test("Teardown cancelRecomposer=$cancelRecomposer key=$useKey") {
                runTest {
                    val events = mutableListOf<String>()
                    val failures = mutableListOf<Throwable>()
                    val worker =
                        CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
                    val bridge = fixture.commandQueueBridgeMock
                    every { bridge.cppLoadFile(any(), any(), any()) } answers {
                        worker.onFileLoaded(secondArg(), FileHandle(1))
                        1L
                    }
                    every { bridge.cppCreateDefaultStateMachine(any(), any(), any()) } answers {
                        worker.onStateMachineInstantiated(secondArg(), StateMachineHandle(1))
                        1L
                    }
                    every { bridge.cppDeleteFile(any(), any(), any()) } answers {
                        events.add("file")
                    }
                    every { bridge.cppDeleteStateMachine(any(), any(), any()) } answers {
                        events.add("state machine")
                    }
                    val frameClock = BroadcastFrameClock()
                    val scope = CoroutineScope(
                        SupervisorJob() + StandardTestDispatcher(testScheduler) + frameClock +
                            CoroutineExceptionHandler { _, error -> failures.add(error) },
                    )
                    val recomposer = Recomposer(scope.coroutineContext)
                    val composition = Composition(TeardownApplier(), recomposer)
                    scope.launch { recomposer.runRecomposeAndApplyChanges() }
                    var stateMachine: StateMachine? = null
                    val source = RiveFileSource.Bytes(byteArrayOf())
                    try {
                        composition.setContent {
                            // Same synchronous reference release as rememberRiveWorker.
                            DisposableEffect(worker) {
                                onDispose {
                                    events.add("worker reference")
                                    worker.release("Test", "Compose dispose")
                                }
                            }
                            val file = rememberRiveFile(source, worker)
                            if (file is Result.Success) {
                                val artboard = remember(file.value) {
                                    Artboard(
                                        ArtboardHandle(1),
                                        worker,
                                        file.value.fileHandle,
                                        null
                                    )
                                }
                                if (useKey) {
                                    key(0) {
                                        stateMachine =
                                            (
                                                rememberStateMachineResult(
                                                    artboard
                                                ) as? Result.Success
                                                )?.value
                                    }
                                } else {
                                    stateMachine =
                                        (
                                            rememberStateMachineResult(
                                                artboard
                                            ) as? Result.Success
                                            )?.value
                                }
                            }
                        }
                        repeat(4) {
                            runCurrent()
                            Snapshot.sendApplyNotifications()
                            runCurrent()
                            frameClock.sendFrame(it.toLong())
                        }
                        runCurrent()
                        check(stateMachine != null) { "State machine was not composed" }
                        worker.refCount shouldBe 2

                        // Window/lifecycle shutdown can cancel effects before composition disposal.
                        if (cancelRecomposer) recomposer.cancel()
                        composition.dispose()
                        runCurrent()

                        println("cancel=$cancelRecomposer key=$useKey events=$events")
                        failures shouldBe emptyList()
                        stateMachine!!.closed shouldBe true
                        worker.refCount shouldBe 0
                        if (cancelRecomposer) {
                            events shouldBe listOf("worker reference", "file")
                        } else {
                            events shouldBe listOf("worker reference", "state machine", "file")
                        }
                    } finally {
                        composition.dispose()
                        recomposer.cancel()
                        scope.cancel()
                        runCurrent()
                        // Do not let the native shutdown thread outlive the fixture's bridge mocks.
                        worker.awaitShutdown(5_000) shouldBe true
                    }
                }
            }
        }
    }
})

/** Runs effect-only compositions without Android UI nodes. */
private class TeardownApplier : AbstractApplier<Unit>(Unit) {
    /** No nodes are emitted by the test content. */
    override fun insertTopDown(index: Int, instance: Unit) = Unit

    /** No nodes are emitted by the test content. */
    override fun insertBottomUp(index: Int, instance: Unit) = Unit

    /** No nodes are emitted by the test content. */
    override fun remove(index: Int, count: Int) = Unit

    /** No nodes are emitted by the test content. */
    override fun move(from: Int, to: Int, count: Int) = Unit

    /** No nodes are retained by this applier. */
    override fun onClear() = Unit
}
