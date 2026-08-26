package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveAndroidTest
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveResourceClosedException
import app.rive.ViewModelSource
import app.rive.rememberViewModelInstanceResult
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Compose tests for remembered view model instances. */
@RunWith(AndroidJUnit4::class)
class ViewModelInstanceComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies a file change synchronously resets a remembered VMI result. */
    @Test
    fun fileChange_reportsLoadingSynchronously() {
        val files = runBlocking {
            List(2) {
                RiveFile.load(
                    RiveFileSource.RawRes(R.raw.data_bind_test_impl, context.resources),
                    riveWorker,
                )
            }
        }
        val source = ViewModelSource.Named("Test All").blankInstance()

        try {
            composeRule.assertResultResetsToLoading(
                resourceName = "ViewModelInstance",
                withNativePollingPaused = ::withRiveWorkerPollingPaused,
                result = { useReplacement ->
                    rememberViewModelInstanceResult(
                        files[useReplacement.toIndex()],
                        source,
                    )
                },
                assertClosed = { instance ->
                    assertFailsWith<RiveResourceClosedException> { instance.checkOpen() }
                },
            )
        } finally {
            files.asReversed().forEach(RiveFile::close)
        }
    }

    /** Verifies a source change synchronously resets a remembered VMI result. */
    @Test
    fun sourceChange_reportsLoadingSynchronously() {
        val file = runBlocking {
            RiveFile.load(
                RiveFileSource.RawRes(R.raw.data_bind_test_impl, context.resources),
                riveWorker,
            )
        }
        val viewModel = ViewModelSource.Named("Test All")
        val sources = listOf(viewModel.blankInstance(), viewModel.defaultInstance())

        try {
            composeRule.assertResultResetsToLoading(
                resourceName = "ViewModelInstance",
                withNativePollingPaused = ::withRiveWorkerPollingPaused,
                result = { useReplacement ->
                    rememberViewModelInstanceResult(
                        file,
                        sources[useReplacement.toIndex()],
                    )
                },
                assertClosed = { instance ->
                    assertFailsWith<RiveResourceClosedException> { instance.checkOpen() }
                },
            )
        } finally {
            file.close()
        }
    }

    /** Verifies a file change resets a VMI that uses its implicit default source. */
    @Test
    fun fileChange_withImplicitDefaultSource_reportsLoadingSynchronously() {
        val files = runBlocking {
            List(2) {
                RiveFile.load(
                    RiveFileSource.RawRes(R.raw.empty, context.resources),
                    riveWorker,
                )
            }
        }

        try {
            composeRule.assertResultResetsToLoading(
                resourceName = "implicit ViewModelInstance chain",
                withNativePollingPaused = ::withRiveWorkerPollingPaused,
                result = { useReplacement ->
                    rememberViewModelInstanceResult(files[useReplacement.toIndex()])
                },
                assertClosed = { instance ->
                    assertFailsWith<RiveResourceClosedException> { instance.checkOpen() }
                },
            )
        } finally {
            files.asReversed().forEach(RiveFile::close)
        }
    }
}
