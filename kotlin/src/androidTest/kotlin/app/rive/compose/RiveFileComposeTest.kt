package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveAndroidTest
import app.rive.RiveFileSource
import app.rive.RiveResourceClosedException
import app.rive.core.CommandQueue
import app.rive.core.CommandQueuePoller
import app.rive.core.assertDisposed
import app.rive.rememberRiveFile
import app.rive.runtime.kotlin.test.R
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Compose tests for remembered Rive files. */
@RunWith(AndroidJUnit4::class)
class RiveFileComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies a source change synchronously resets a remembered file result. */
    @Test
    fun sourceChange_reportsLoadingSynchronously() {
        val bytes = context.resources.openRawResource(R.raw.empty).use { it.readBytes() }
        val sources = listOf(
            RiveFileSource.Bytes(bytes),
            RiveFileSource.Bytes(bytes.copyOf()),
        )

        composeRule.assertResultResetsToLoading(
            resourceName = "RiveFile",
            withNativePollingPaused = ::withRiveWorkerPollingPaused,
            result = { useReplacement ->
                rememberRiveFile(sources[useReplacement.toIndex()], riveWorker)
            },
            assertClosed = { file ->
                assertFailsWith<RiveResourceClosedException> { file.checkOpen() }
            },
        )
    }

    /** Verifies a worker change synchronously resets a remembered file result. */
    @Test
    fun workerChange_reportsLoadingSynchronously() {
        val workers = listOf(CommandQueue(), CommandQueue())
        val poller = CommandQueuePoller(workers)
        val source = RiveFileSource.RawRes(R.raw.empty, context.resources)

        try {
            composeRule.assertResultResetsToLoading(
                resourceName = "RiveFile",
                withNativePollingPaused = poller::withPollingPaused,
                result = { useReplacement ->
                    rememberRiveFile(source, workers[useReplacement.toIndex()])
                },
                assertClosed = { file ->
                    assertFailsWith<RiveResourceClosedException> { file.checkOpen() }
                },
            )
        } finally {
            poller.close()
            workers.forEach { worker ->
                if (!worker.isDisposed) {
                    worker.release(javaClass.simpleName, "Test cleanup")
                }
                assertDisposed(worker)
            }
        }
    }
}
