package app.rive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.testutil.RuntimeComposition
import app.rive.testutil.installTraceMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Tests [rememberRiveFile] loading and recomposition behavior on the JVM.
 *
 * Uses a real Composition and Recomposer with mocked JNI and Android tracing. Native load
 * responses are controlled by each test to exercise completed and in-progress loads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RiveFileComposeUnitTest : FunSpec({
    val fixture = installCommandQueueTestFixture()
    installTraceMock()

    for (initialDeferred in listOf(false, true)) {
        for (completeBeforeToggle in listOf(false, true)) {
            test("File toggles initialDeferred=$initialDeferred completed=$completeBeforeToggle") {
                runTest {
                    FileCompositionHost(this, fixture, initialDeferred).use { host ->
                        host.runUntilIdle()
                        val first = host.loads.single()
                        first.deferDecoding shouldBe initialDeferred
                        host.result shouldBe Result.Loading
                        if (completeBeforeToggle) host.complete(first)
                        val originalFile = (host.result as? Result.Success)?.value

                        host.deferDecoding = !initialDeferred
                        host.runUntilIdle()
                        if (initialDeferred) {
                            // A deferred load is replaced by eager loading, even while pending.
                            host.result shouldBe Result.Loading
                            host.loads.map { it.deferDecoding } shouldBe listOf(true, false)
                            host.deletedHandles shouldBe listOf(first.handle.handle)
                            originalFile?.closed?.shouldBe(true)
                        } else {
                            host.loads.size shouldBe 1
                            host.deletedHandles shouldBe emptyList()
                            if (completeBeforeToggle) {
                                (host.result as Result.Success).value shouldBe originalFile
                            } else {
                                host.result shouldBe Result.Loading
                            }
                        }

                        // Returning to the original argument must retain the eager generation.
                        host.deferDecoding = initialDeferred
                        host.runUntilIdle()
                        host.loads.size shouldBe if (initialDeferred) 2 else 1
                        val retained = host.loads.last()
                        retained.deferDecoding shouldBe false
                        if (initialDeferred && !completeBeforeToggle) {
                            // A late response from the cancelled deferred load must not replace it.
                            host.complete(first)
                            host.result shouldBe Result.Loading
                            host.deletedHandles shouldBe listOf(first.handle.handle)
                        }
                        if (initialDeferred || !completeBeforeToggle) host.complete(retained)
                        val eagerFile = (host.result as Result.Success).value
                        eagerFile.fileHandle shouldBe retained.handle
                        eagerFile.closed shouldBe false
                        repeat(3) {
                            host.deferDecoding = !host.deferDecoding
                            host.runUntilIdle()
                            (host.result as Result.Success).value shouldBe eagerFile
                        }
                        host.loads.size shouldBe if (initialDeferred) 2 else 1
                    }
                }
            }
        }
    }

    for (replaceWorker in listOf(false, true)) {
        for (completeBeforeChange in listOf(false, true)) {
            test("File policy resets worker=$replaceWorker completed=$completeBeforeChange") {
                runTest {
                    FileCompositionHost(this, fixture, initialDeferred = false).use { host ->
                        host.runUntilIdle()
                        val first = host.loads.single()
                        if (completeBeforeChange) host.complete(first)
                        val originalFile = (host.result as? Result.Success)?.value
                        host.deferDecoding = true
                        host.runUntilIdle()
                        host.loads.size shouldBe 1

                        if (replaceWorker) {
                            host.worker = host.newWorker()
                        } else {
                            host.source = RiveFileSource.Bytes(byteArrayOf(2))
                        }
                        host.runUntilIdle()
                        host.result shouldBe Result.Loading
                        host.loads.map { it.deferDecoding } shouldBe listOf(false, true)
                        host.deletedHandles shouldBe listOf(first.handle.handle)
                        originalFile?.closed?.shouldBe(true)
                        if (!completeBeforeChange) {
                            host.complete(first)
                            host.result shouldBe Result.Loading
                        }
                        val replacement = host.loads.last()
                        host.complete(replacement)
                        (host.result as Result.Success).value.fileHandle shouldBe replacement.handle
                    }
                }
            }
        }
    }
})

/** A dispatched file load whose native response is controlled by the test. */
private data class PendingFileLoad(
    val worker: CommandQueue,
    val requestId: Long,
    val handle: FileHandle,
    val deferDecoding: Boolean,
)

/** Runs a remembered file in a real composition with explicitly delayed native responses. */
@OptIn(ExperimentalCoroutinesApi::class)
private class FileCompositionHost(
    testScope: TestScope,
    private val fixture: CommandQueueTestFixture,
    initialDeferred: Boolean,
) : AutoCloseable {
    private val workers = mutableListOf<CommandQueue>()
    var worker by mutableStateOf(newWorker())
    var source by mutableStateOf<RiveFileSource>(RiveFileSource.Bytes(byteArrayOf(1)))
    var deferDecoding by mutableStateOf(initialDeferred)
    var result: Result<RiveFile> = Result.Loading
        private set
    val loads = mutableListOf<PendingFileLoad>()
    val deletedHandles = mutableListOf<Long>()
    private val composition = RuntimeComposition(testScope)

    init {
        every { fixture.commandQueueBridgeMock.cppLoadFile(any(), any(), any(), any()) } answers {
            val handle = FileHandle(loads.size + 1L)
            loads.add(PendingFileLoad(worker, secondArg(), handle, arg(3)))
            handle.handle
        }
        every { fixture.commandQueueBridgeMock.cppDeleteFile(any(), any(), any()) } answers {
            deletedHandles.add(thirdArg())
            Unit
        }
        composition.setContent { result = rememberRiveFile(source, worker, deferDecoding) }
    }

    /** Creates a worker whose initial reference is released when the host closes. */
    fun newWorker(): CommandQueue =
        CommandQueue(fixture.renderContextMock, fixture.commandQueueBridgeMock).also(workers::add)

    /** Applies pending state changes and runs the corresponding loading effects. */
    fun runUntilIdle() = composition.runUntilIdle()

    /** Delivers a native load response and lets Compose observe the result. */
    fun complete(load: PendingFileLoad) {
        load.worker.onFileLoaded(load.requestId, load.handle)
        runUntilIdle()
    }

    /** Disposes all effects and waits for worker shutdown before fixture mocks are cleared. */
    override fun close() {
        try {
            composition.close()
        } finally {
            workers.forEach { worker ->
                worker.release("Test", "File composition cleanup")
                worker.awaitShutdown(5_000) shouldBe true
                worker.refCount shouldBe 0
            }
        }
    }
}
