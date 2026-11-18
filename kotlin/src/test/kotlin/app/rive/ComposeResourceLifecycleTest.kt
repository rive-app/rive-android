package app.rive

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.StateMachineHandle
import app.rive.testutil.RuntimeComposition
import app.rive.testutil.installTraceMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    installTraceMock()

    for (cancelRecomposer in listOf(false, true)) {
        for (useKey in listOf(false, true)) {
            test("Teardown cancelRecomposer=$cancelRecomposer key=$useKey") {
                runTest {
                    val events = mutableListOf<String>()
                    val worker =
                        CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock)
                    val bridge = fixture.commandQueueBridgeMock
                    every { bridge.cppLoadFile(any(), any(), any(), any()) } answers {
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
                    val composition = RuntimeComposition(this)
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
                        composition.runUntilIdle()
                        check(stateMachine != null) { "State machine was not composed" }
                        worker.refCount shouldBe 2

                        // Window/lifecycle shutdown can cancel effects before composition disposal.
                        if (cancelRecomposer) composition.cancelRecomposer()
                        composition.dispose()

                        stateMachine!!.closed shouldBe true
                        worker.refCount shouldBe 0
                        if (cancelRecomposer) {
                            events shouldBe listOf("worker reference", "file")
                        } else {
                            events shouldBe listOf("worker reference", "state machine", "file")
                        }
                    } finally {
                        composition.close()
                        // Do not let the native shutdown thread outlive the fixture's bridge mocks.
                        worker.awaitShutdown(5_000) shouldBe true
                    }
                }
            }
        }
    }
})
